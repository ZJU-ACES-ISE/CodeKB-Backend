package com.codekb.repo;

import com.codekb.analysis.RepoSummaryRepository;
import com.codekb.common.BusinessException;
import com.codekb.event.RepoImportedEvent;
import com.codekb.graph.GraphZipUploadService;
import com.codekb.graph.OssService;
import com.codekb.graph.RepoGraphTask;
import com.codekb.graph.RepoGraphTaskRepository;
import com.codekb.knowledge.KnowledgeBaseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

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

    public KbRepoService(KbRepoRepository repoRepo,
                         KnowledgeBaseService kbService,
                         ApplicationEventPublisher eventPublisher,
                         RepoSummaryRepository summaryRepository,
                         RepoGraphTaskRepository graphTaskRepository,
                         @Lazy OssService ossService,
                         @Lazy GraphZipUploadService graphZipUploadService) {
        this.repoRepo = repoRepo;
        this.kbService = kbService;
        this.eventPublisher = eventPublisher;
        this.summaryRepository = summaryRepository;
        this.graphTaskRepository = graphTaskRepository;
        this.ossService = ossService;
        this.graphZipUploadService = graphZipUploadService;
    }

    @Transactional
    public KbRepo importRepo(Long kbId, String githubUrl, String providerHint, String ref, Integer depth, Long userId) {
        kbService.getById(kbId); // 404 guard

        KbRepo entity = new KbRepo();
        entity.setKbId(kbId);
        entity.setGithubUrl(githubUrl);
        entity.setRef(ref);
        entity.setCreatedBy(userId);
        RepoUrlParts parts = RepoUrlParser.parse(githubUrl, providerHint);
        entity.setProvider(parts.provider().key());
        entity.setOwner(parts.owner());
        entity.setRepo(parts.repo());
        entity.setName(parts.name());

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

    public List<KbRepo> listByKb(Long kbId) {
        return repoRepo.findByKbId(kbId);
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
        repoRepo.findById(repoId).ifPresent(r -> {
            if (language != null) r.setLanguage(language);
            if (starCount != null) r.setStarCount(starCount);
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
}
