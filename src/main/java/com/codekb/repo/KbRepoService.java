package com.codekb.repo;

import com.codekb.analysis.FrameworkInference;
import com.codekb.analysis.RepoSummary;
import com.codekb.analysis.RepoSummaryRepository;
import com.codekb.common.BusinessException;
import com.codekb.event.RepoImportedEvent;
import com.codekb.event.ZipGraphUploadRequestedEvent;
import com.codekb.graph.GraphZipUploadService;
import com.codekb.graph.OssService;
import com.codekb.graph.RepoGraphTask;
import com.codekb.graph.RepoGraphTaskRepository;
import com.codekb.knowledge.KnowledgeBaseService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class KbRepoService {

    private static final Logger log = LoggerFactory.getLogger(KbRepoService.class);
    private static final LocalDateTime LEGACY_TIMESTAMP_BASE = LocalDateTime.of(2026, 5, 17, 23, 0, 0);
    private static final int LEGACY_TIMESTAMP_WINDOW_SECONDS = 10 * 60;

    public record ImportRepoResult(KbRepo repo, boolean duplicate) {
        public String action() {
            return duplicate ? "DUPLICATE" : "IMPORTED";
        }
    }

    private final KbRepoRepository repoRepo;
    private final KnowledgeBaseService kbService;
    private final ApplicationEventPublisher eventPublisher;
    private final RepoSummaryRepository summaryRepository;
    private final RepoGraphTaskRepository graphTaskRepository;
    private final OssService ossService;
    private final GraphZipUploadService graphZipUploadService;
    private final LocalRepoZipService localRepoZipService;
    private final ObjectMapper objectMapper;

    public KbRepoService(KbRepoRepository repoRepo,
                         KnowledgeBaseService kbService,
                         ApplicationEventPublisher eventPublisher,
                         RepoSummaryRepository summaryRepository,
                         RepoGraphTaskRepository graphTaskRepository,
                         @Lazy OssService ossService,
                         @Lazy GraphZipUploadService graphZipUploadService,
                         LocalRepoZipService localRepoZipService,
                         ObjectMapper objectMapper) {
        this.repoRepo = repoRepo;
        this.kbService = kbService;
        this.eventPublisher = eventPublisher;
        this.summaryRepository = summaryRepository;
        this.graphTaskRepository = graphTaskRepository;
        this.ossService = ossService;
        this.graphZipUploadService = graphZipUploadService;
        this.localRepoZipService = localRepoZipService;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void backfillLegacyRepoTimestamps() {
        int updatedCount = backfillMissingTimestamps(repoRepo.findByCreatedAtIsNullOrUpdatedAtIsNull());
        if (updatedCount > 0) {
            log.info("Backfilled timestamps for {} legacy repos", updatedCount);
        }
    }

    @Transactional
    public ImportRepoResult importRepo(Long kbId,
                                       String githubUrl,
                                       String providerHint,
                                       String repoName,
                                       String ref,
                                       Integer depth,
                                       Long userId) {
        RepoUrlParts parts = RepoUrlParser.parse(githubUrl, providerHint);
        if (parts.provider() == RepoProvider.LOCAL) {
            return new ImportRepoResult(importLocalDirectory(kbId, githubUrl, repoName, ref, userId), false);
        }

        kbService.getOwnedById(userId, kbId);

        Optional<KbRepo> duplicate = findDuplicateRemoteRepo(parts, userId);
        if (duplicate.isPresent()) {
            return new ImportRepoResult(duplicate.get(), true);
        }

        KbRepo entity = new KbRepo();
        entity.setKbId(kbId);
        entity.setGithubUrl(githubUrl);
        entity.setRef(ref);
        entity.setCreatedBy(userId);
        entity.setProvider(parts.provider().key());
        entity.setOwner(parts.owner());
        entity.setRepo(parts.repo());
        entity.setName(normalizeRepoName(repoName, parts.name()));

        KbRepo saved = repoRepo.save(entity);
        kbService.incrementRepoCount(kbId);
        eventPublisher.publishEvent(new RepoImportedEvent(this, saved.getId()));
        return new ImportRepoResult(saved, false);
    }

    /**
     * 上传 ZIP：仓库标记为 upload://，构图走 Graph 服务 multipart /api/graph-jobs/upload。
     */
    @Transactional
    public KbRepo importZip(Long kbId, MultipartFile file, String repoNameOpt, Long userId) throws java.io.IOException {
        kbService.getOwnedById(userId, kbId);
        if (file == null || file.isEmpty()) {
            throw new BusinessException(400, "请上传 ZIP 文件");
        }
        String fn = file.getOriginalFilename();
        if (fn == null || fn.isBlank()) {
            fn = "upload.zip";
        }
        if (!fn.toLowerCase().endsWith(".zip")) {
            throw new BusinessException(400, "仅支持 .zip 压缩包");
        }

        byte[] zipBytes = file.getBytes();
        String stem = fn.replaceAll("(?i)\\.zip$", "");
        String displayName = (repoNameOpt != null && !repoNameOpt.isBlank()) ? repoNameOpt.trim() : stem;
        if (displayName.isBlank()) {
            displayName = "uploaded-repo";
        }

        KbRepo entity = new KbRepo();
        entity.setKbId(kbId);
        entity.setGithubUrl("upload://" + displayName);
        entity.setProvider(RepoProvider.ZIP.key());
        entity.setRef("");
        entity.setCreatedBy(userId);
        entity.setName(displayName);

        KbRepo saved = repoRepo.save(entity);
        kbService.incrementRepoCount(kbId);
        eventPublisher.publishEvent(new RepoImportedEvent(this, saved.getId()));
        eventPublisher.publishEvent(new ZipGraphUploadRequestedEvent(this, saved.getId(), zipBytes, fn, repoNameOpt));
        return saved;
    }

    /**
     * 本地目录：后端直接读取服务器上的绝对路径，打包为 ZIP 后复用现有 Graph 上传链路。
     */
    @Transactional
    public KbRepo importLocalDirectory(Long kbId, String githubUrl, String repoName, String ref, Long userId) {
        kbService.getOwnedById(userId, kbId);
        String localPath = extractLocalPath(githubUrl);
        LocalRepoZipService.LocalRepoDescriptor descriptor = localRepoZipService.describe(localPath, repoName);

        KbRepo entity = new KbRepo();
        entity.setKbId(kbId);
        entity.setGithubUrl(githubUrl);
        entity.setProvider(RepoProvider.LOCAL.key());
        entity.setRef(ref);
        entity.setCreatedBy(userId);
        entity.setName(normalizeRepoName(repoName, descriptor.repoName()));

        KbRepo saved = repoRepo.save(entity);
        kbService.incrementRepoCount(kbId);
        eventPublisher.publishEvent(new RepoImportedEvent(this, saved.getId()));
        return saved;
    }

    public List<KbRepo> listByKb(Long kbId) {
        List<KbRepo> repos = repoRepo.findByKbId(kbId);
        backfillMissingTimestamps(repos);
        return repos;
    }

    public List<Map<String, Object>> listRepoViewsByKb(Long kbId) {
        List<KbRepo> repos = repoRepo.findByKbId(kbId);
        backfillMissingTimestamps(repos);
        return toRepoViews(repos);
    }

    public List<Map<String, Object>> listRepoViewsByOwnedKb(Long userId, Long kbId) {
        kbService.getOwnedById(userId, kbId);
        return listRepoViewsByKb(kbId);
    }

    public KbRepo getById(Long id) {
        return repoRepo.findById(id)
                .orElseThrow(() -> new BusinessException(404, "仓库不存在: " + id));
    }

    public KbRepo getOwnedById(Long userId, Long repoId) {
        KbRepo repo = getById(repoId);
        kbService.getOwnedById(userId, repo.getKbId());
        return repo;
    }

    @Transactional
    public KbRepo refreshRepo(Long userId, Long repoId) {
        KbRepo repo = getOwnedById(userId, repoId);
        summaryRepository.deleteByRepoId(repoId);
        repo.setStatus("IMPORTED");
        return repoRepo.save(repo);
    }

    @Transactional
    public void updateStatus(Long repoId, String status) {
        repoRepo.findById(repoId).ifPresent(r -> {
            r.setStatus(status);
            repoRepo.save(r);
        });
    }

    @Transactional
    public void updateLanguageAndStar(Long repoId, String language, Integer starCount) {
        updateSummaryFields(repoId, language, starCount, null, null);
    }

    @Transactional
    public void updateSummaryFields(Long repoId,
                                    String language,
                                    Integer starCount,
                                    String defaultBranch,
                                    String framework) {
        repoRepo.findById(repoId).ifPresent(r -> {
            if (language != null && !language.isBlank()) r.setLanguage(language);
            if (starCount != null) r.setStarCount(starCount);
            if (defaultBranch != null && !defaultBranch.isBlank()) r.setDefaultBranch(defaultBranch);
            if (framework != null && !framework.isBlank()) r.setFramework(framework);
            repoRepo.save(r);
        });
    }

    /**
     * 级联删除仓库：repo_summary -> repo_graph_task -> OSS 图快照 -> kb_repo -> KB 计数 -1。
     * OSS 删除是 best-effort，失败不会回滚。
     */
    @Transactional
    public void deleteRepo(Long userId, Long repoId) {
        KbRepo repo = getOwnedById(userId, repoId);
        Long kbId = repo.getKbId();

        // 1) 先把所有图任务捞出来，记下 snapshot key，待事务结束后异步清理 OSS
        List<RepoGraphTask> tasks = graphTaskRepository.findByRepoId(repoId);

        // 2) 删 summary / graph_task / kb_repo
        summaryRepository.deleteByRepoId(repoId);
        graphTaskRepository.deleteByRepoId(repoId);
        repoRepo.delete(repo);

        // 3) KB 计数
        kbService.decrementRepoCount(kbId);

        // 4) OSS 清理（best effort，不影响主流程）
        for (RepoGraphTask t : tasks) {
            String key = t.getSnapshotUrl();
            if (key == null || key.isBlank()) continue;
            try {
                ossService.deleteObject(key);
            } catch (Exception e) {
                log.warn("Failed to delete OSS object {}: {}", key, e.getMessage());
            }
        }
        log.info("Deleted repo {} (kbId={}), cascaded {} graph tasks", repoId, kbId, tasks.size());
    }

    public List<KbRepo> listOwnedRepos(Long userId) {
        List<Long> kbIds = kbService.listByOwner(userId).stream()
                .map(kb -> kb.getId())
                .toList();
        if (kbIds.isEmpty()) {
            return List.of();
        }
        List<KbRepo> repos = repoRepo.findByKbIdIn(kbIds);
        backfillMissingTimestamps(repos);
        return repos;
    }

    public Map<String, Object> toRepoView(KbRepo repo) {
        RepoSummary summary = summaryRepository.findByRepoId(repo.getId()).orElse(null);
        List<RepoGraphTask> tasks = graphTaskRepository.findByRepoIdOrderByCreatedAtDesc(repo.getId());
        RepoGraphTask latestGraphTask = currentLatestGraphTask(repo, tasks);
        return toRepoView(repo, summary, latestGraphTask);
    }

    public Map<String, Object> toRepoView(KbRepo repo, RepoSummary summary, RepoGraphTask latestGraphTask) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", repo.getId());
        view.put("kbId", repo.getKbId());
        view.put("name", repo.getName());
        view.put("owner", repo.getOwner());
        view.put("repo", repo.getRepo());
        view.put("provider", repo.getProvider());
        view.put("githubUrl", repo.getGithubUrl());
        view.put("ref", repo.getRef());
        view.put("defaultBranch", firstNonBlank(repo.getDefaultBranch(), summary != null ? summary.getDefaultBranch() : null));
        view.put("language", firstNonBlank(repo.getLanguage(), summary != null ? summary.getPrimaryLanguage() : null));
        view.put("framework", firstNonBlank(repo.getFramework(), frameworkLabel(summary)));
        view.put("starCount", repo.getStarCount());
        view.put("status", normalizeRepoStatus(repo.getStatus(), summary != null));
        view.put("createdBy", repo.getCreatedBy());
        view.put("createdAt", repo.getCreatedAt());
        view.put("updatedAt", repo.getUpdatedAt());
        view.put("latestGraphTask", latestGraphTask);
        return view;
    }

    public String normalizeRepoStatus(String rawStatus, boolean hasSummary) {
        if (hasSummary || "SUMMARIZED".equals(rawStatus)) {
            return "SUMMARIZED";
        }
        if ("FAILED".equals(rawStatus)) {
            return "FAILED";
        }
        return "IMPORTED";
    }

    private List<Map<String, Object>> toRepoViews(List<KbRepo> repos) {
        if (repos.isEmpty()) {
            return List.of();
        }

        List<Long> repoIds = repos.stream().map(KbRepo::getId).toList();
        Map<Long, List<RepoGraphTask>> tasksByRepo = graphTasksByRepo(repoIds);
        Map<Long, RepoSummary> summariesByRepoId = summaryRepository.findByRepoIdIn(repoIds).stream()
                .collect(Collectors.toMap(RepoSummary::getRepoId, Function.identity(), (a, b) -> a));

        return repos.stream()
                .map(repo -> toRepoView(
                        repo,
                        summariesByRepoId.get(repo.getId()),
                        currentLatestGraphTask(repo, tasksByRepo.getOrDefault(repo.getId(), List.of()))))
                .toList();
    }

    public RepoGraphTask currentLatestGraphTask(KbRepo repo, List<RepoGraphTask> tasks) {
        if (tasks == null || tasks.isEmpty()) {
            return null;
        }
        LocalDateTime cutoff = repo.getUpdatedAt() != null ? repo.getUpdatedAt() : repo.getCreatedAt();
        if (cutoff == null) {
            return tasks.get(0);
        }
        for (RepoGraphTask task : tasks) {
            LocalDateTime createdAt = task.getCreatedAt();
            if (createdAt == null || !createdAt.isBefore(cutoff)) {
                return task;
            }
        }
        return null;
    }

    private String extractLocalPath(String githubUrl) {
        if (githubUrl == null || !githubUrl.startsWith("local://")) {
            throw new BusinessException(400, "本地目录地址必须使用 local:// 前缀");
        }
        String localPath = githubUrl.substring("local://".length()).trim();
        if (localPath.isBlank()) {
            throw new BusinessException(400, "本地目录路径不能为空");
        }
        return localPath;
    }

    private String normalizeRepoName(String requestedName, String fallbackName) {
        if (requestedName != null && !requestedName.isBlank()) {
            return requestedName.trim();
        }
        return fallbackName;
    }

    private Optional<KbRepo> findDuplicateRemoteRepo(RepoUrlParts parts, Long userId) {
        if (userId == null || !parts.hasRemoteProject()) {
            return Optional.empty();
        }
        RepoProvider provider = parts.provider();
        if (provider != RepoProvider.GITHUB && provider != RepoProvider.GITEE && provider != RepoProvider.GITLAB) {
            return Optional.empty();
        }
        return repoRepo.findFirstByCreatedByAndProviderIgnoreCaseAndOwnerIgnoreCaseAndRepoIgnoreCaseOrderByIdAsc(
                userId, provider.key(), parts.owner(), parts.repo());
    }

    private Map<Long, List<RepoGraphTask>> graphTasksByRepo(Collection<Long> repoIds) {
        Map<Long, List<RepoGraphTask>> tasksByRepo = new HashMap<>();
        for (RepoGraphTask task : graphTaskRepository.findByRepoIdInOrderByRepoIdAscCreatedAtDesc(repoIds)) {
            tasksByRepo.computeIfAbsent(task.getRepoId(), ignored -> new ArrayList<>())
                    .add(task);
        }
        return tasksByRepo;
    }

    private String frameworkLabel(RepoSummary summary) {
        if (summary == null) {
            return null;
        }
        List<String> labels = FrameworkInference.labelsFromFrameworksJson(summary.getFrameworks(), objectMapper);
        if (labels.isEmpty()) {
            labels = FrameworkInference.labelsFromTopicsJson(summary.getTopics(), objectMapper);
        }
        return labels.isEmpty() ? null : labels.get(0);
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        if (second != null && !second.isBlank()) {
            return second;
        }
        return null;
    }

    @Transactional
    protected int backfillMissingTimestamps(Collection<KbRepo> repos) {
        List<KbRepo> toUpdate = repos.stream()
                .filter(repo -> repo.getCreatedAt() == null || repo.getUpdatedAt() == null)
                .peek(repo -> {
                    LocalDateTime fallback = randomLegacyTimestamp();
                    LocalDateTime createdAt = repo.getCreatedAt();
                    LocalDateTime updatedAt = repo.getUpdatedAt();

                    if (createdAt == null && updatedAt == null) {
                        createdAt = fallback;
                        updatedAt = fallback;
                    } else if (createdAt == null) {
                        createdAt = updatedAt != null ? updatedAt : fallback;
                    } else if (updatedAt == null) {
                        updatedAt = createdAt;
                    }

                    repo.setCreatedAt(createdAt);
                    repo.setUpdatedAt(updatedAt);
                })
                .toList();
        if (toUpdate.isEmpty()) {
            return 0;
        }
        repoRepo.saveAll(toUpdate);
        return toUpdate.size();
    }

    private LocalDateTime randomLegacyTimestamp() {
        int offset = ThreadLocalRandom.current().nextInt(LEGACY_TIMESTAMP_WINDOW_SECONDS);
        return LEGACY_TIMESTAMP_BASE.plusSeconds(offset);
    }
}
