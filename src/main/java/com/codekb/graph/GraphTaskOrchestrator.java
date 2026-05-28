package com.codekb.graph;

import com.codekb.config.GraphServiceProperties;
import com.codekb.event.GraphJobRequestedEvent;
import com.codekb.repo.KbRepoService;
import com.codekb.repo.LocalRepoZipService;
import com.codekb.repo.RepoProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.OptimisticLockException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class GraphTaskOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(GraphTaskOrchestrator.class);
    static final Object REMOTE_SUBMISSION_MONITOR = new Object();
    private static final Set<GraphTaskStatus> ACTIVE_STATUSES =
            Set.of(GraphTaskStatus.PENDING, GraphTaskStatus.SUBMITTED, GraphTaskStatus.BUILDING, GraphTaskStatus.SLOW_BUILDING);
    private static final Set<GraphTaskStatus> ALL_REMOTE_ACTIVE_STATUSES =
            Set.of(GraphTaskStatus.SUBMITTED, GraphTaskStatus.BUILDING, GraphTaskStatus.SLOW_BUILDING);

    private final AtomicLong submitBlockedUntilEpochMs = new AtomicLong(0L);

    private final RepoGraphTaskRepository taskRepo;
    private final GraphServiceClient client;
    private final OssService ossService;
    private final KbRepoService repoService;
    private final ObjectMapper objectMapper;
    private final GraphServiceProperties props;
    private final LocalRepoZipService localRepoZipService;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${codekb.oss.key-prefix:codekb/snapshots/}")
    private String ossKeyPrefix;

    public GraphTaskOrchestrator(RepoGraphTaskRepository taskRepo,
                                 GraphServiceClient client,
                                 OssService ossService,
                                 KbRepoService repoService,
                                 ObjectMapper objectMapper,
                                 GraphServiceProperties props,
                                 LocalRepoZipService localRepoZipService,
                                 ApplicationEventPublisher eventPublisher) {
        this.taskRepo = taskRepo;
        this.client = client;
        this.ossService = ossService;
        this.repoService = repoService;
        this.objectMapper = objectMapper;
        this.props = props;
        this.localRepoZipService = localRepoZipService;
        this.eventPublisher = eventPublisher;
    }

    @Async("codekbGraphExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handle(GraphJobRequestedEvent event) {
        Long repoId = event.getRepoId();
        Long taskId = event.getTaskId();
        try {
            if (taskId == null) {
                log.info("Graph job requested for repoId={}", repoId);
                var repo = repoService.getById(repoId);
                if (repo.getGithubUrl() != null && repo.getGithubUrl().startsWith("upload://")) {
                    log.info("Skip generic graph event for ZIP upload repoId={}", repoId);
                    return;
                }
                RepoGraphTask task = createAndSubmitTask(
                        repoId,
                        repo.getGithubUrl(),
                        repo.getRef() != null ? repo.getRef() : "",
                        1);
                dispatchTask(task.getId(), "event-new");
                return;
            }
            dispatchTask(taskId, "event");
        } catch (Exception e) {
            log.error("Graph job request setup failed repoId={} taskId={}: {}", repoId, taskId, e.getMessage(), e);
        }
    }

    @Scheduled(fixedDelayString = "${codekb.graph.recovery-interval-ms:1500}")
    public void recoverPendingTasks() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime staleBefore = now.minusNanos(props.getStaleTaskThresholdMs() * 1_000_000L);
        List<RepoGraphTask> remoteTasks = taskRepo.findDispatchableRemoteTasks(
                ALL_REMOTE_ACTIVE_STATUSES,
                now,
                staleBefore,
                PageRequest.of(0, props.getRecoveryBatchSize()));
        for (RepoGraphTask task : remoteTasks) {
            eventPublisher.publishEvent(new GraphJobRequestedEvent(this, task.getRepoId(), task.getId()));
        }

        int submitSlots = availableRemoteSubmissionSlots();
        if (submitSlots <= 0 || isSubmissionCoolingDown()) {
            return;
        }

        List<RepoGraphTask> pendingTasks = taskRepo.findDispatchablePendingTasks(
                GraphTaskStatus.PENDING,
                now,
                staleBefore,
                PageRequest.of(0, Math.min(submitSlots, props.getRecoveryBatchSize())));
        for (RepoGraphTask task : pendingTasks) {
            eventPublisher.publishEvent(new GraphJobRequestedEvent(this, task.getRepoId(), task.getId()));
        }
    }

    @Async("codekbGraphExecutor")
    public void dispatchTask(Long taskId, String trigger) {
        try {
            if (!tryAcquireLease(taskId)) {
                return;
            }
            RepoGraphTask task = taskRepo.findById(taskId)
                    .orElseThrow(() -> new IllegalStateException("Graph task not found: " + taskId));
            advanceTask(task, trigger);
        } catch (ObjectOptimisticLockingFailureException | OptimisticLockException e) {
            log.info("Graph task lease rotated taskId={} trigger={} message={}", taskId, trigger, e.getMessage());
        } catch (Exception e) {
            log.error("Graph task dispatch failed taskId={} trigger={}: {}", taskId, trigger, e.getMessage(), e);
            taskRepo.findById(taskId).ifPresent(task -> failTask(task, e.getMessage(), true));
        }
    }

    @Transactional
    public RepoGraphTask createAndSubmitTask(Long repoId, String githubUrl, String ref, int depth) {
        String normalizedRef = ref != null ? ref : "";
        int normalizedDepth = depth < 0 ? 1 : depth;
        var existing = taskRepo.findFirstByRepoIdAndGithubUrlAndRefAndDepthAndStatusInOrderByCreatedAtDesc(
                repoId,
                githubUrl,
                normalizedRef,
                normalizedDepth,
                ACTIVE_STATUSES);
        if (existing.isPresent()) {
            return existing.get();
        }

        RepoGraphTask task = new RepoGraphTask();
        task.setRepoId(repoId);
        task.setGithubUrl(githubUrl);
        task.setRef(normalizedRef);
        task.setDepth(normalizedDepth);
        task.setStatus(GraphTaskStatus.PENDING);
        task.setNextPollAt(LocalDateTime.now());
        task.setLeaseExpiresAt(null);
        return taskRepo.save(task);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RepoGraphTask save(RepoGraphTask task) {
        return taskRepo.save(task);
    }

    private void advanceTask(RepoGraphTask task, String trigger) throws Exception {
        if (task.getGraphJobId() == null || task.getGraphJobId().isBlank()) {
            if (task.getGithubUrl() != null && task.getGithubUrl().startsWith("upload://")) {
                failTask(task, "ZIP graph task missing uploaded job id", true);
                return;
            }
            submitGraphJobSerial(task, trigger);
            return;
        }

        if (taskTooOld(task)) {
            failTask(task, "Graph task exceeded max runtime " + props.getMaxTaskRuntimeMs() + "ms", true);
            return;
        }

        pollGraphJobOnce(task, trigger);
    }

    private void submitGraphJobSerial(RepoGraphTask task, String trigger) throws Exception {
        synchronized (REMOTE_SUBMISSION_MONITOR) {
            RepoGraphTask latest = taskRepo.findById(task.getId())
                    .orElseThrow(() -> new IllegalStateException("Graph task not found: " + task.getId()));
            if (latest.getGraphJobId() != null && !latest.getGraphJobId().isBlank()) {
                return;
            }
            if (!canSubmitNewRemoteJob()) {
                reschedulePendingTask(latest, "pending-limit");
                log.debug("Graph task submit gated taskId={} trigger={}", latest.getId(), trigger);
                return;
            }
            submitGraphJob(latest);
        }
    }

    private void submitGraphJob(RepoGraphTask task) throws Exception {
        try {
            var repo = repoService.getById(task.getRepoId());
            String jobId;
            if (RepoProvider.LOCAL.key().equals(repo.getProvider())) {
                LocalRepoZipService.LocalRepoArchive archive =
                        localRepoZipService.archive(extractLocalPath(repo.getGithubUrl()), repo.getName());
                Map<String, Object> created = client.uploadJob(archive.bytes(), archive.filename(), archive.repoName());
                jobId = extractJobId(created);
                log.info("Local graph job submitted via ZIP upload: jobId={} taskId={}", jobId, task.getId());
            } else {
                Map<String, Object> created = client.createJob(task.getGithubUrl(), safeRef(task), safeDepth(task));
                jobId = extractJobId(created);
                log.info("Graph job submitted: jobId={} taskId={}", jobId, task.getId());
            }

            task.setGraphJobId(jobId);
            task.setStatus(GraphTaskStatus.SUBMITTED);
            task.setSubmittedAt(LocalDateTime.now());
            scheduleNextPoll(task, GraphTaskStatus.SUBMITTED, props.getPollIntervalMs());
            save(task);
        } catch (Exception e) {
            if (looksLikeRemoteOverload(e)) {
                blockSubmissionsFor(props.getRemoteErrorBackoffMs());
                reschedulePendingTask(task, "remote-backoff");
                log.warn("Graph submission deferred taskId={} due to remote pressure: {}", task.getId(), e.getMessage());
                return;
            }
            throw e;
        }
    }

    private void pollGraphJobOnce(RepoGraphTask task, String trigger) throws Exception {
        Map<String, Object> status = client.getJobStatus(task.getGraphJobId());
        String externalStatus = String.valueOf(status.getOrDefault("status", "unknown"));
        task.setExternalStatusRaw(externalStatus);

        if ("completed".equals(externalStatus)) {
            completeTask(task);
            return;
        }
        if ("failed".equals(externalStatus)) {
            failTask(task, String.valueOf(status.getOrDefault("error", "external service failed")), true);
            return;
        }

        GraphTaskStatus nextStatus = resolveNextStatus(externalStatus);
        scheduleNextPoll(task, nextStatus, props.getPollIntervalMs());
        save(task);
        log.debug("Graph task yielded taskId={} trigger={} externalStatus={}", task.getId(), trigger, externalStatus);
    }

    private void completeTask(RepoGraphTask task) throws Exception {
        Map<String, Object> graph = client.getGraph(task.getGraphJobId());
        String json = objectMapper.writeValueAsString(graph);
        String key = ossKeyPrefix + task.getId() + ".json";
        ossService.uploadJson(key, json);

        Object nodeCount = ((Map<?, ?>) graph.getOrDefault("metadata", Map.of())).get("node_count");
        Object edgeCount = ((Map<?, ?>) graph.getOrDefault("metadata", Map.of())).get("edge_count");
        task.setNodeCount(nodeCount != null ? ((Number) nodeCount).intValue() : null);
        task.setEdgeCount(edgeCount != null ? ((Number) edgeCount).intValue() : null);
        task.setSnapshotUrl(key);
        task.setStatus(GraphTaskStatus.READY);
        task.setCompletedAt(LocalDateTime.now());
        task.setLeaseExpiresAt(null);
        task.setNextPollAt(null);
        task.setErrorMessage(null);
        save(task);
        log.info("Graph task READY: taskId={} nodes={} edges={}", task.getId(), task.getNodeCount(), task.getEdgeCount());
    }

    private void failTask(RepoGraphTask task, String errorMessage, boolean clearLease) {
        task.setStatus(GraphTaskStatus.FAILED);
        task.setErrorMessage(errorMessage);
        task.setNextPollAt(null);
        if (clearLease) {
            task.setLeaseExpiresAt(null);
        }
        save(task);
        log.warn("Graph task FAILED: taskId={} error={}", task.getId(), errorMessage);
    }

    private void scheduleNextPoll(RepoGraphTask task, GraphTaskStatus status, long delayMs) {
        LocalDateTime now = LocalDateTime.now();
        task.setStatus(status);
        task.setLeaseExpiresAt(now.minusSeconds(1));
        task.setNextPollAt(now.plusNanos(Math.max(0L, delayMs) * 1_000_000L));
    }

    private void reschedulePendingTask(RepoGraphTask task, String reason) {
        LocalDateTime now = LocalDateTime.now();
        task.setStatus(GraphTaskStatus.PENDING);
        task.setLeaseExpiresAt(null);
        task.setNextPollAt(now.plusNanos(props.getPendingRetryDelayMs() * 1_000_000L));
        save(task);
        log.debug("Graph task kept queued taskId={} reason={}", task.getId(), reason);
    }

    private boolean tryAcquireLease(Long taskId) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime leaseExpiresAt = now.plusNanos(props.getWorkerLeaseMs() * 1_000_000L);
        return taskRepo.tryAcquireLease(taskId, leaseExpiresAt, ACTIVE_STATUSES, now) > 0;
    }

    private boolean taskTooOld(RepoGraphTask task) {
        if (props.getMaxTaskRuntimeMs() <= 0) {
            return false;
        }
        LocalDateTime baseline = task.getSubmittedAt() != null ? task.getSubmittedAt() : task.getCreatedAt();
        if (baseline == null) {
            return false;
        }
        return !baseline.plusNanos(props.getMaxTaskRuntimeMs() * 1_000_000L).isAfter(LocalDateTime.now());
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

    private int safeDepth(RepoGraphTask task) {
        return task.getDepth() < 0 ? 1 : task.getDepth();
    }

    private String safeRef(RepoGraphTask task) {
        return task.getRef() != null ? task.getRef() : "";
    }

    boolean canSubmitNewRemoteJob() {
        if (isSubmissionCoolingDown()) {
            return false;
        }
        long activeCount = taskRepo.countByStatusInAndGraphJobIdPresent(ALL_REMOTE_ACTIVE_STATUSES);
        long limit = Math.max(1, props.getRemoteMaxConcurrentJobs());
        return activeCount < limit;
    }

    private int availableRemoteSubmissionSlots() {
        if (isSubmissionCoolingDown()) {
            return 0;
        }
        long activeCount = taskRepo.countByStatusInAndGraphJobIdPresent(ALL_REMOTE_ACTIVE_STATUSES);
        long limit = Math.max(1, props.getRemoteMaxConcurrentJobs());
        return (int) Math.max(0L, limit - activeCount);
    }

    private GraphTaskStatus resolveNextStatus(String externalStatus) {
        if ("queued".equals(externalStatus) || "created".equals(externalStatus)) {
            return GraphTaskStatus.SUBMITTED;
        }
        return GraphTaskStatus.BUILDING;
    }

    private boolean isSubmissionCoolingDown() {
        return submitBlockedUntilEpochMs.get() > System.currentTimeMillis();
    }

    private void blockSubmissionsFor(long millis) {
        long delay = Math.max(millis, props.getSubmissionCooldownMs());
        long until = System.currentTimeMillis() + Math.max(0L, delay);
        submitBlockedUntilEpochMs.accumulateAndGet(until, Math::max);
    }

    private boolean looksLikeRemoteOverload(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return false;
        }
        String lower = message.toLowerCase();
        return lower.contains("timed out")
                || lower.contains("timeout")
                || lower.contains("connection refused")
                || lower.contains("connect timed out")
                || lower.contains("i/o error")
                || lower.contains("503")
                || lower.contains("502")
                || lower.contains("500");
    }
}
