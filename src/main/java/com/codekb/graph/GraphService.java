package com.codekb.graph;

import com.codekb.common.BusinessException;
import com.codekb.event.GraphJobRequestedEvent;
import com.codekb.repo.KbRepoService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class GraphService {

    private static final int DEFAULT_COMPACT_NODE_LIMIT = 1200;
    private static final int DEFAULT_COMPACT_EDGE_LIMIT = 6000;
    private static final int MAX_COMPACT_NODE_LIMIT = 4000;
    private static final int MAX_COMPACT_EDGE_LIMIT = 20000;

    private final RepoGraphTaskRepository taskRepo;
    private final GraphServiceClient client;
    private final OssService ossService;
    private final KbRepoService repoService;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    public GraphService(RepoGraphTaskRepository taskRepo,
                        GraphServiceClient client,
                        OssService ossService,
                        KbRepoService repoService,
                        ApplicationEventPublisher eventPublisher,
                        ObjectMapper objectMapper) {
        this.taskRepo = taskRepo;
        this.client = client;
        this.ossService = ossService;
        this.repoService = repoService;
        this.eventPublisher = eventPublisher;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public RepoGraphTask createTask(Long userId, Long repoId, String ref, Integer depth) {
        var repo = repoService.getOwnedById(userId, repoId);
        String normalizedRef = ref != null ? ref : repo.getRef();
        int normalizedDepth = depth != null ? depth : 1;
        RepoGraphTask saved = taskRepo.findFirstByRepoIdAndGithubUrlAndRefAndDepthAndStatusInOrderByCreatedAtDesc(
                        repoId,
                        repo.getGithubUrl(),
                        normalizedRef != null ? normalizedRef : "",
                        normalizedDepth < 0 ? 1 : normalizedDepth,
                        java.util.Set.of(
                                GraphTaskStatus.PENDING,
                                GraphTaskStatus.SUBMITTED,
                                GraphTaskStatus.BUILDING,
                                GraphTaskStatus.SLOW_BUILDING))
                .orElseGet(() -> {
                    RepoGraphTask task = new RepoGraphTask();
                    task.setRepoId(repoId);
                    task.setGithubUrl(repo.getGithubUrl());
                    task.setRef(normalizedRef != null ? normalizedRef : "");
                    task.setDepth(normalizedDepth < 0 ? 1 : normalizedDepth);
                    task.setStatus(GraphTaskStatus.PENDING);
                    return taskRepo.save(task);
                });
        eventPublisher.publishEvent(new GraphJobRequestedEvent(this, repoId, saved.getId()));
        return saved;
    }

    public List<RepoGraphTask> listTasksByRepo(Long userId, Long repoId) {
        repoService.getOwnedById(userId, repoId);
        return taskRepo.findByRepoIdOrderByCreatedAtDesc(repoId);
    }

    public RepoGraphTask getLatestTask(Long userId, Long repoId) {
        repoService.getOwnedById(userId, repoId);
        return taskRepo.findFirstByRepoIdOrderByCreatedAtDesc(repoId)
                .orElseThrow(() -> new BusinessException(404, "\u8be5\u4ed3\u5e93\u6682\u65e0\u56fe\u4efb\u52a1"));
    }

    public RepoGraphTask getTask(Long userId, Long taskId) {
        RepoGraphTask task = taskRepo.findById(taskId)
                .orElseThrow(() -> new BusinessException(404, "\u56fe\u4efb\u52a1\u4e0d\u5b58\u5728: " + taskId));
        repoService.getOwnedById(userId, task.getRepoId());
        return task;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getGraph(Long userId, Long taskId) {
        return getGraph(userId, taskId, false, null, null);
    }

    public Map<String, Object> getGraph(Long userId, Long taskId, boolean compact, Integer nodeLimit, Integer edgeLimit) {
        RepoGraphTask task = getReadyTask(userId, taskId);
        Map<String, Object> graph = loadGraph(task);
        if (!compact) {
            return withTaskId(graph, task.getId());
        }
        return withTaskId(toCompactGraph(graph, normalizeNodeLimit(nodeLimit), normalizeEdgeLimit(edgeLimit)), task.getId());
    }

    public Map<String, Object> getGraphNode(Long userId, Long taskId, String nodeId) {
        RepoGraphTask task = getReadyTask(userId, taskId);
        if (nodeId == null || nodeId.isBlank()) {
            throw new BusinessException(400, "nodeId \u4e0d\u80fd\u4e3a\u7a7a");
        }
        List<Map<String, Object>> nodes = asMapList(loadGraph(task).get("nodes"));
        for (Map<String, Object> node : nodes) {
            if (nodeId.equals(String.valueOf(node.getOrDefault("id", "")))) {
                return node;
            }
        }
        throw new BusinessException(404, "\u56fe\u8282\u70b9\u4e0d\u5b58\u5728: " + nodeId);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getLatestReadyGraph(Long userId, Long repoId) {
        return getLatestReadyGraph(userId, repoId, false, null, null);
    }

    public Map<String, Object> getLatestReadyGraph(Long userId, Long repoId, boolean compact, Integer nodeLimit, Integer edgeLimit) {
        repoService.getOwnedById(userId, repoId);
        RepoGraphTask task = taskRepo.findFirstByRepoIdAndStatusOrderByCreatedAtDesc(repoId, GraphTaskStatus.READY)
                .orElseThrow(() -> new BusinessException(404, "\u8be5\u4ed3\u5e93\u6682\u65e0\u5c31\u7eea\u7684\u5173\u8054\u56fe"));
        Map<String, Object> graph = loadGraph(task);
        if (!compact) {
            return withTaskId(graph, task.getId());
        }
        return withTaskId(toCompactGraph(graph, normalizeNodeLimit(nodeLimit), normalizeEdgeLimit(edgeLimit)), task.getId());
    }

    private RepoGraphTask getReadyTask(Long userId, Long taskId) {
        RepoGraphTask task = getTask(userId, taskId);
        if (task.getStatus() != GraphTaskStatus.READY) {
            throw new BusinessException(409, "\u56fe\u4efb\u52a1\u5c1a\u672a\u5b8c\u6210\uff0c\u5f53\u524d\u72b6\u6001: " + task.getStatus());
        }
        return task;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> loadGraph(RepoGraphTask task) {
        if (task.getSnapshotUrl() != null) {
            String json = ossService.downloadJson(task.getSnapshotUrl());
            if (json != null) {
                try {
                    return objectMapper.readValue(json, Map.class);
                } catch (Exception ignored) {}
            }
        }

        if (task.getGraphJobId() != null) {
            return client.getGraph(task.getGraphJobId());
        }

        throw new BusinessException(500, "\u65e0\u6cd5\u83b7\u53d6\u56fe\u6570\u636e");
    }

    private int normalizeNodeLimit(Integer requested) {
        if (requested == null || requested <= 0) {
            return DEFAULT_COMPACT_NODE_LIMIT;
        }
        return Math.min(requested, MAX_COMPACT_NODE_LIMIT);
    }

    private int normalizeEdgeLimit(Integer requested) {
        if (requested == null || requested <= 0) {
            return DEFAULT_COMPACT_EDGE_LIMIT;
        }
        return Math.min(requested, MAX_COMPACT_EDGE_LIMIT);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> asMapList(Object value) {
        if (value instanceof List<?> list) {
            List<Map<String, Object>> result = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    result.add((Map<String, Object>) map);
                }
            }
            return result;
        }
        return List.of();
    }

    private Map<String, Object> toCompactGraph(Map<String, Object> graph, int nodeLimit, int edgeLimit) {
        List<Map<String, Object>> rawNodes = asMapList(graph.get("nodes"));
        List<Map<String, Object>> rawEdges = asMapList(graph.get("edges"));

        Map<String, Integer> degree = new HashMap<>();
        for (Map<String, Object> edge : rawEdges) {
            String source = String.valueOf(edge.getOrDefault("source", ""));
            String target = String.valueOf(edge.getOrDefault("target", ""));
            if (!source.isBlank()) {
                degree.put(source, degree.getOrDefault(source, 0) + 1);
            }
            if (!target.isBlank()) {
                degree.put(target, degree.getOrDefault(target, 0) + 1);
            }
        }

        List<Map<String, Object>> rankedNodes = new ArrayList<>(rawNodes);
        rankedNodes.sort(Comparator
                .<Map<String, Object>>comparingInt(node -> -degree.getOrDefault(String.valueOf(node.getOrDefault("id", "")), 0))
                .thenComparing(node -> String.valueOf(node.getOrDefault("id", ""))));

        List<Map<String, Object>> keptNodes = new ArrayList<>();
        Set<String> keptNodeIds = new HashSet<>();
        int limitedNodeCount = Math.min(nodeLimit, rankedNodes.size());
        for (int i = 0; i < limitedNodeCount; i++) {
            Map<String, Object> rawNode = rankedNodes.get(i);
            keptNodes.add(compactNode(rawNode));
            keptNodeIds.add(String.valueOf(rawNode.getOrDefault("id", "")));
        }

        List<Map<String, Object>> filteredEdges = new ArrayList<>();
        for (Map<String, Object> edge : rawEdges) {
            String source = String.valueOf(edge.getOrDefault("source", ""));
            String target = String.valueOf(edge.getOrDefault("target", ""));
            if (keptNodeIds.contains(source) && keptNodeIds.contains(target)) {
                filteredEdges.add(new LinkedHashMap<>(edge));
            }
        }

        filteredEdges.sort(Comparator
                .<Map<String, Object>>comparingInt(edge ->
                        -(degree.getOrDefault(String.valueOf(edge.getOrDefault("source", "")), 0)
                                + degree.getOrDefault(String.valueOf(edge.getOrDefault("target", "")), 0)))
                .thenComparing(edge -> String.valueOf(edge.getOrDefault("source", "")))
                .thenComparing(edge -> String.valueOf(edge.getOrDefault("target", ""))));

        if (filteredEdges.size() > edgeLimit) {
            filteredEdges = new ArrayList<>(filteredEdges.subList(0, edgeLimit));
        }

        boolean truncated = keptNodes.size() < rawNodes.size() || filteredEdges.size() < rawEdges.size();
        Map<String, Object> result = new LinkedHashMap<>();
        if (graph.containsKey("job")) {
            result.put("job", graph.get("job"));
        }
        result.put("metadata", compactMetadata(graph.get("metadata"), rawNodes.size(), rawEdges.size(), keptNodes.size(), filteredEdges.size(), truncated));
        result.put("nodes", keptNodes);
        result.put("edges", filteredEdges);
        return result;
    }

    private Map<String, Object> compactNode(Map<String, Object> rawNode) {
        Map<String, Object> node = new LinkedHashMap<>(rawNode);
        node.remove("code");
        return node;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> compactMetadata(Object metadataValue,
                                                int fullNodeCount,
                                                int fullEdgeCount,
                                                int returnedNodeCount,
                                                int returnedEdgeCount,
                                                boolean truncated) {
        Map<String, Object> metadata = metadataValue instanceof Map<?, ?> map
                ? new LinkedHashMap<>((Map<String, Object>) map)
                : new LinkedHashMap<>();
        metadata.put("full_node_count", fullNodeCount);
        metadata.put("full_edge_count", fullEdgeCount);
        metadata.put("returned_node_count", returnedNodeCount);
        metadata.put("returned_edge_count", returnedEdgeCount);
        metadata.put("compact", true);
        metadata.put("truncated", truncated);
        metadata.put("node_code_omitted", true);
        return metadata;
    }

    private Map<String, Object> withTaskId(Map<String, Object> graph, Long taskId) {
        Map<String, Object> result = new LinkedHashMap<>(graph);
        result.put("taskId", taskId);
        return result;
    }
}
