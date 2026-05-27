package com.codekb.graph;

import com.codekb.repo.KbRepoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;

@Component
public class GraphZipUploadService {

    private static final Logger log = LoggerFactory.getLogger(GraphZipUploadService.class);

    private final GraphServiceClient client;
    private final GraphTaskOrchestrator orchestrator;
    private final KbRepoService repoService;

    public GraphZipUploadService(GraphServiceClient client,
                                 GraphTaskOrchestrator orchestrator,
                                 KbRepoService repoService) {
        this.client = client;
        this.orchestrator = orchestrator;
        this.repoService = repoService;
    }

    @Async("codekbGraphExecutor")
    public void submit(Long repoId, byte[] zipBytes, String originalFilename, String repoNameOverride) {
        RepoGraphTask task = new RepoGraphTask();
        try {
            var repo = repoService.getById(repoId);
            task.setRepoId(repoId);
            task.setGithubUrl(repo.getGithubUrl());
            task.setRef(repo.getRef() != null ? repo.getRef() : "");
            task.setDepth(1);
            task.setStatus(GraphTaskStatus.PENDING);
            task = orchestrator.save(task);

            Map<String, Object> created = client.uploadJob(zipBytes, originalFilename, repoNameOverride);
            String jobId = extractJobId(created);
            if (jobId == null || jobId.isBlank() || "null".equals(jobId)) {
                throw new IllegalStateException("Graph 上传响应缺少 job_id: " + created);
            }

            task.setGraphJobId(jobId);
            task.setStatus(GraphTaskStatus.SUBMITTED);
            task.setSubmittedAt(LocalDateTime.now());
            task = orchestrator.save(task);
            log.info("ZIP graph job submitted: jobId={} taskId={}", jobId, task.getId());

            orchestrator.pollJobToCompletion(task, repoId, jobId);
        } catch (Exception e) {
            log.error("ZIP graph upload failed repoId={}: {}", repoId, e.getMessage(), e);
            if (task.getId() != null) {
                task.setStatus(GraphTaskStatus.FAILED);
                task.setErrorMessage(e.getMessage());
                orchestrator.save(task);
            }
        }
    }

    private static String extractJobId(Map<String, Object> body) {
        Object v = body.get("job_id");
        if (v == null) {
            v = body.get("jobId");
        }
        if (v == null) {
            return null;
        }
        return String.valueOf(v);
    }
}
