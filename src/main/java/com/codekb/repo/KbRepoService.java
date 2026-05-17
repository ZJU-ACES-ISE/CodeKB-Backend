package com.codekb.repo;

import com.codekb.analysis.FrameworkInference;
import com.codekb.analysis.RepoSummary;
import com.codekb.analysis.RepoSummaryRepository;
import com.codekb.common.BusinessException;
import com.codekb.event.RepoImportedEvent;
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

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class KbRepoService {

    private static final Logger log = LoggerFactory.getLogger(KbRepoService.class);

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

    @Transactional
    public KbRepo importRepo(Long kbId,
                             String githubUrl,
                             String providerHint,
                             String repoName,
                             String ref,
                             Integer depth,
                             Long userId) {
        RepoUrlParts parts = RepoUrlParser.parse(githubUrl, providerHint);
        if (parts.provider() == RepoProvider.LOCAL) {
            return importLocalDirectory(kbId, githubUrl, repoName, ref, userId);
        }

        kbService.getById(kbId); // 404 guard

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
        return saved;
    }

    /**
     * 上传 ZIP：仓库标记为 upload://，构图走 Graph 服务 multipart /api/graph-jobs/upload。
     */
    @Transactional
    public KbRepo importZip(Long kbId, MultipartFile file, String repoNameOpt, Long userId) throws java.io.IOException {
        kbService.getById(kbId);
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
        graphZipUploadService.submit(saved.getId(), zipBytes, fn, repoNameOpt);
        return saved;
    }

    /**
     * 本地目录：后端直接读取服务器上的绝对路径，打包为 ZIP 后复用现有 Graph 上传链路。
     */
    @Transactional
    public KbRepo importLocalDirectory(Long kbId, String githubUrl, String repoName, String ref, Long userId) {
        kbService.getById(kbId);
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
        return repoRepo.findByKbId(kbId);
    }

    public List<Map<String, Object>> listRepoViewsByKb(Long kbId) {
        return toRepoViews(repoRepo.findByKbId(kbId));
    }

    public KbRepo getById(Long id) {
        return repoRepo.findById(id)
                .orElseThrow(() -> new BusinessException(404, "仓库不存在: " + id));
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
    public void deleteRepo(Long repoId) {
        KbRepo repo = getById(repoId);
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

    public Map<String, Object> toRepoView(KbRepo repo) {
        RepoSummary summary = summaryRepository.findByRepoId(repo.getId()).orElse(null);
        RepoGraphTask latestGraphTask = graphTaskRepository.findFirstByRepoIdOrderByCreatedAtDesc(repo.getId()).orElse(null);
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
        Map<Long, RepoSummary> summariesByRepoId = summaryRepository.findByRepoIdIn(repoIds).stream()
                .collect(Collectors.toMap(RepoSummary::getRepoId, Function.identity(), (a, b) -> a));
        Map<Long, RepoGraphTask> latestGraphTasks = latestGraphTasks(repoIds);

        return repos.stream()
                .map(repo -> toRepoView(repo, summariesByRepoId.get(repo.getId()), latestGraphTasks.get(repo.getId())))
                .toList();
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

    private Map<Long, RepoGraphTask> latestGraphTasks(Collection<Long> repoIds) {
        Map<Long, RepoGraphTask> latestTasks = new HashMap<>();
        for (RepoGraphTask task : graphTaskRepository.findByRepoIdInOrderByRepoIdAscCreatedAtDesc(repoIds)) {
            latestTasks.putIfAbsent(task.getRepoId(), task);
        }
        return latestTasks;
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
}
