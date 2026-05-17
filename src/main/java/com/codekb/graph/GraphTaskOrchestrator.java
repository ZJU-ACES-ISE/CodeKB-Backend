package com.codekb.graph;

import com.codekb.config.GraphServiceProperties;
import com.codekb.event.GraphJobRequestedEvent;
import com.codekb.repo.KbRepoService;
import com.codekb.repo.LocalRepoZipService;
import com.codekb.repo.RepoProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDateTime;
import java.util.Map;

@Component
public class GraphTaskOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(GraphTaskOrchestrator.class);

    private final RepoGraphTaskRepository taskRepo;
    private final GraphServiceClient client;
    private final OssService ossService;
    private final KbRepoService repoService;
    private final ObjectMapper objectMapper;
    private final GraphServiceProperties props;
    private final LocalRepoZipService localRepoZipService;

    @Value("${codekb.oss.key-prefix:codekb/snapshots/}")
    private String ossKeyPrefix;

    public GraphTaskOrchestrator(RepoGraphTaskRepository taskRepo,
                                 GraphServiceClient client,
                                 OssService ossService,
                                 KbRepoService repoService,
                                 ObjectMapper objectMapper,
                                 GraphServiceProperties props,
                                 LocalRepoZipService localRepoZipService) {
        this.taskRepo = taskRepo;
        this.client = client;
        this.ossService = ossService;
        this.repoService = repoService;
        this.objectMapper = objectMapper;
        this.props = props;
        this.localRepoZipService = localRepoZipService;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handle(GraphJobRequestedEvent event) {
        Long repoId = event.getRepoId();
        Long taskId = event.getTaskId();
        log.info("Graph job requested for repoId={}", repoId);

        RepoGraphTask task = null;
        try {
            var repo = repoService.getById(repoId);
            if (repo.getGithubUrl() != null && repo.getGithubUrl().startsWith("upload://")) {
                log.info("Skip git graph job for ZIP upload repoId={}", repoId);
                return;
            }
            if (taskId != null) {
                task = taskRepo.findById(taskId)
                        .orElseThrow(() -> new IllegalStateException("图任务不存在: " + taskId));
            } else {
                task = new RepoGraphTask();
                task.setRepoId(repoId);
                task.setGithubUrl(repo.getGithubUrl());
                task.setRef(repo.getRef());
                task.setDepth(1);
                task.setStatus(GraphTaskStatus.PENDING);
                task = save(task);
            }

            String ref = task.getRef() != null ? task.getRef() : repo.getRef();
            int depth = task.getDepth() < 0 ? 1 : task.getDepth();
            task.setGithubUrl(repo.getGithubUrl());
            task.setRef(ref);
            task.setDepth(depth);
            task.setStatus(GraphTaskStatus.PENDING);
            task = save(task);

            String jobId;
            if (RepoProvider.LOCAL.key().equals(repo.getProvider())) {
                LocalRepoZipService.LocalRepoArchive archive =
                        localRepoZipService.archive(extractLocalPath(repo.getGithubUrl()), repo.getName());
                Map<String, Object> created = client.uploadJob(archive.bytes(), archive.filename(), archive.repoName());
                jobId = extractJobId(created);
                log.info("Local graph job submitted via ZIP upload: jobId={} taskId={}", jobId, task.getId());
            } else {
                Map<String, Object> created = client.createJob(repo.getGithubUrl(), ref, depth);
                jobId = extractJobId(created);
                log.info("Graph job submitted: jobId={} taskId={}", jobId, task.getId());
            }

            task.setGraphJobId(jobId);
            task.setStatus(GraphTaskStatus.SUBMITTED);
            task.setSubmittedAt(LocalDateTime.now());
            task = save(task);

            pollJobToCompletion(task, repoId, jobId);
        } catch (Exception e) {
            log.error("Graph orchestration error repoId={}: {}", repoId, e.getMessage(), e);
            if (task != null && task.getId() != null) {
                task.setStatus(GraphTaskStatus.FAILED);
                task.setErrorMessage(e.getMessage());
                save(task);
            }
        }
    }

    public void pollJobToCompletion(RepoGraphTask task, Long repoId, String jobId) throws Exception {
        int maxPolls = props.getMaxPollTimes();
        long interval = props.getPollIntervalMs();
        for (int i = 0; i < maxPolls; i++) {
            Thread.sleep(interval);
            Map<String, Object> status = client.getJobStatus(jobId);
            String ext = String.valueOf(status.getOrDefault("status", "unknown"));
            task.setExternalStatusRaw(ext);

            if ("completed".equals(ext)) {
                Map<String, Object> graph = client.getGraph(jobId);
                String json = objectMapper.writeValueAsString(graph);
                String key = ossKeyPrefix + task.getId() + ".json";
                ossService.uploadJson(key, json);

                Object nc = ((Map<?, ?>) graph.getOrDefault("metadata", Map.of())).get("node_count");
                Object ec = ((Map<?, ?>) graph.getOrDefault("metadata", Map.of())).get("edge_count");
                task.setNodeCount(nc != null ? ((Number) nc).intValue() : null);
                task.setEdgeCount(ec != null ? ((Number) ec).intValue() : null);
                task.setSnapshotUrl(key);
                task.setStatus(GraphTaskStatus.READY);
                task.setCompletedAt(LocalDateTime.now());
                save(task);
                log.info("Graph task READY: taskId={} nodes={} edges={}", task.getId(), task.getNodeCount(), task.getEdgeCount());
                return;
            } else if ("failed".equals(ext)) {
                task.setStatus(GraphTaskStatus.FAILED);
                task.setErrorMessage(String.valueOf(status.getOrDefault("error", "external service failed")));
                save(task);
                log.warn("Graph task FAILED: taskId={}", task.getId());
                return;
            } else {
                task.setStatus(GraphTaskStatus.BUILDING);
                save(task);
            }

            if (i % 20 == 0) {
                log.info("Polling graph job {} attempt {}/{}", jobId, i + 1, maxPolls);
            }
        }

        task.setStatus(GraphTaskStatus.FAILED);
        task.setErrorMessage("超过最大轮询次数 " + maxPolls);
        save(task);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RepoGraphTask save(RepoGraphTask task) {
        return taskRepo.save(task);
    }

    private String extractJobId(Map<String, Object> body) {
        Object value = body.get("job_id");
        if (value == null) {
            value = body.get("jobId");
        }
        if (value == null) {
            throw new IllegalStateException("Graph response missing job_id: " + body);
        }
        return String.valueOf(value);
    }

    private String extractLocalPath(String githubUrl) {
        if (githubUrl == null || !githubUrl.startsWith("local://")) {
            throw new IllegalArgumentException("Invalid local repo url: " + githubUrl);
        }
        String localPath = githubUrl.substring("local://".length()).trim();
        if (localPath.isBlank()) {
            throw new IllegalArgumentException("Local repo path is blank");
        }
        return localPath;
    }
}
