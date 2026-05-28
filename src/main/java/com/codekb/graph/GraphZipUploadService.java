package com.codekb.graph;

import com.codekb.repo.KbRepoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;

@Component
public class GraphZipUploadService {

    private static final Logger log = LoggerFactory.getLogger(GraphZipUploadService.class);
    private static final Set<GraphTaskStatus> ACTIVE_STATUSES =
            Set.of(
                    GraphTaskStatus.PENDING,
                    GraphTaskStatus.SUBMITTED,
                    GraphTaskStatus.BUILDING,
                    GraphTaskStatus.SLOW_BUILDING);

    private final GraphServiceClient client;
    private final GraphTaskOrchestrator orchestrator;
    private final KbRepoService repoService;
    private final RepoGraphTaskRepository taskRepo;

    public GraphZipUploadService(GraphServiceClient client,
                                 GraphTaskOrchestrator orchestrator,
                                 KbRepoService repoService,
                                 RepoGraphTaskRepository taskRepo) {
        this.client = client;
        this.orchestrator = orchestrator;
        this.repoService = repoService;
        this.taskRepo = taskRepo;
    }

    @Async("codekbGraphExecutor")
    public void submit(Long repoId, byte[] zipBytes, String originalFilename, String repoNameOverride) {
        RepoGraphTask task = new RepoGraphTask();
        try {
            var repo = repoService.getById(repoId);
            String ref = repo.getRef() != null ? repo.getRef() : "";
            task = taskRepo.findFirstByRepoIdAndGithubUrlAndRefAndDepthAndStatusInOrderByCreatedAtDesc(
                            repoId,
                            repo.getGithubUrl(),
                            ref,
                            1,
                            ACTIVE_STATUSES)
                    .orElseGet(() -> {
                        RepoGraphTask created = new RepoGraphTask();
                        created.setRepoId(repoId);
                        created.setGithubUrl(repo.getGithubUrl());
                        created.setRef(ref);
                        created.setDepth(1);
                        created.setStatus(GraphTaskStatus.PENDING);
                        return orchestrator.save(created);
                    });

            if (task.getGraphJobId() != null && !task.getGraphJobId().isBlank()) {
                log.info("Reuse active ZIP graph task taskId={} repoId={}", task.getId(), repoId);
                orchestrator.dispatchTask(task.getId(), "zip-upload-reuse");
                return;
            }

            String jobId = waitAndUploadJob(zipBytes, originalFilename, repoNameOverride);
            if (jobId == null || jobId.isBlank() || "null".equals(jobId)) {
                throw new IllegalStateException("Graph upload response missing job_id");
            }

            task.setGraphJobId(jobId);
            task.setStatus(GraphTaskStatus.SUBMITTED);
            task.setSubmittedAt(LocalDateTime.now());
            task.setLeaseExpiresAt(LocalDateTime.now().minusSeconds(1));
            task.setNextPollAt(LocalDateTime.now());
            task = orchestrator.save(task);
            log.info("ZIP graph job submitted: jobId={} taskId={}", jobId, task.getId());
            orchestrator.dispatchTask(task.getId(), "zip-upload");
        } catch (Exception e) {
            log.error("ZIP graph upload failed repoId={}: {}", repoId, e.getMessage(), e);
            if (task.getId() != null) {
                task.setStatus(GraphTaskStatus.FAILED);
                task.setErrorMessage(e.getMessage());
                task.setLeaseExpiresAt(null);
                task.setNextPollAt(null);
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

    private String waitAndUploadJob(byte[] zipBytes, String originalFilename, String repoNameOverride) throws Exception {
        while (true) {
            synchronized (GraphTaskOrchestrator.REMOTE_SUBMISSION_MONITOR) {
                if (orchestrator.canSubmitNewRemoteJob()) {
                    Map<String, Object> created = client.uploadJob(zipBytes, originalFilename, repoNameOverride);
                    return extractJobId(created);
                }
            }
            Thread.sleep(1000L);
        }
    }
}
