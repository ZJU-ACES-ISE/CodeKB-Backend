package com.codekb.graph;

import com.codekb.common.BusinessException;
import com.codekb.event.GraphJobRequestedEvent;
import com.codekb.repo.KbRepoService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
        eventPublisher.publishEvent(new GraphJobRequestedEvent(this, repoId));
        return saved;
    }

    public RepoGraphTask getTask(Long taskId) {
        return taskRepo.findById(taskId)
                .orElseThrow(() -> new BusinessException(404, "图任务不存在: " + taskId));
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getGraph(Long taskId) {
        RepoGraphTask task = getTask(taskId);
        if (task.getStatus() != GraphTaskStatus.READY) {
            throw new BusinessException(409, "图任务尚未完成，当前状态: " + task.getStatus());
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

        throw new BusinessException(500, "无法获取图数据");
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getLatestReadyGraph(Long repoId) {
        RepoGraphTask task = taskRepo.findFirstByRepoIdAndStatusOrderByCreatedAtDesc(repoId, GraphTaskStatus.READY)
                .orElseThrow(() -> new BusinessException(404, "该仓库暂无就绪的关联图"));
        return getGraph(task.getId());
    }
}
