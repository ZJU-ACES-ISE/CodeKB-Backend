package com.codekb.graph;

import com.codekb.common.BusinessException;
import com.codekb.event.GraphJobRequestedEvent;
import com.codekb.repo.KbRepoService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class GraphService {

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
    public RepoGraphTask createTask(Long repoId, String ref, Integer depth) {
        var repo = repoService.getById(repoId);
        RepoGraphTask task = new RepoGraphTask();
        task.setRepoId(repoId);
        task.setGithubUrl(repo.getGithubUrl());
        task.setRef(ref != null ? ref : repo.getRef());
        task.setDepth(depth != null ? depth : 1);
        task.setStatus(GraphTaskStatus.PENDING);
        RepoGraphTask saved = taskRepo.save(task);
        eventPublisher.publishEvent(new GraphJobRequestedEvent(this, repoId, saved.getId()));
        return saved;
    }

    public List<RepoGraphTask> listTasksByRepo(Long repoId) {
        repoService.getById(repoId);
        return taskRepo.findByRepoIdOrderByCreatedAtDesc(repoId);
    }

    public RepoGraphTask getLatestTask(Long repoId) {
        return taskRepo.findFirstByRepoIdOrderByCreatedAtDesc(repoId)
                .orElseThrow(() -> new BusinessException(404, "\u8be5\u4ed3\u5e93\u6682\u65e0\u56fe\u4efb\u52a1"));
    }

    public RepoGraphTask getTask(Long taskId) {
        return taskRepo.findById(taskId)
                .orElseThrow(() -> new BusinessException(404, "\u56fe\u4efb\u52a1\u4e0d\u5b58\u5728: " + taskId));
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getGraph(Long taskId) {
        RepoGraphTask task = getTask(taskId);
        if (task.getStatus() != GraphTaskStatus.READY) {
            throw new BusinessException(409, "\u56fe\u4efb\u52a1\u5c1a\u672a\u5b8c\u6210\uff0c\u5f53\u524d\u72b6\u6001: " + task.getStatus());
        }

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

    @SuppressWarnings("unchecked")
    public Map<String, Object> getLatestReadyGraph(Long repoId) {
        RepoGraphTask task = taskRepo.findFirstByRepoIdAndStatusOrderByCreatedAtDesc(repoId, GraphTaskStatus.READY)
                .orElseThrow(() -> new BusinessException(404, "\u8be5\u4ed3\u5e93\u6682\u65e0\u5c31\u7eea\u7684\u5173\u8054\u56fe"));
        return getGraph(task.getId());
    }
}
