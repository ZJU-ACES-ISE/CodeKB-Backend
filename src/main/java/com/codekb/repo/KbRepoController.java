package com.codekb.repo;

import com.codekb.analysis.AnalysisService;
import com.codekb.analysis.RepoSummaryRepository;
import com.codekb.auth.CodeKbPrincipal;
import com.codekb.common.ApiResponse;
import com.codekb.graph.RepoGraphTaskRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/repos")
public class KbRepoController {

    private final KbRepoService repoService;
    private final RepoSummaryRepository summaryRepository;
    private final RepoGraphTaskRepository graphTaskRepository;
    private final AnalysisService analysisService;

    public KbRepoController(KbRepoService repoService,
                            RepoSummaryRepository summaryRepository,
                            RepoGraphTaskRepository graphTaskRepository,
                            AnalysisService analysisService) {
        this.repoService = repoService;
        this.summaryRepository = summaryRepository;
        this.graphTaskRepository = graphTaskRepository;
        this.analysisService = analysisService;
    }

    @PostMapping("/import")
    public ApiResponse<Map<String, Object>> importRepo(
            @AuthenticationPrincipal CodeKbPrincipal principal,
            @RequestBody @Valid ImportRequest req) {
        KbRepoService.ImportRepoResult result = repoService.importRepo(
                req.kbId(), req.githubUrl(), req.provider(), req.repoName(), req.ref(),
                req.depth() != null ? req.depth() : 1,
                principal.userId());
        return ApiResponse.ok(buildImportResponse(result.repo(), result.action()));
    }

    @PostMapping(value = "/import-zip", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<Map<String, Object>> importZip(
            @AuthenticationPrincipal CodeKbPrincipal principal,
            @RequestPart("file") MultipartFile file,
            @RequestParam("kbId") Long kbId,
            @RequestParam(value = "repo_name", required = false) String repoName)
            throws java.io.IOException {
        KbRepo saved = repoService.importZip(kbId, file, repoName, principal.userId());
        return ApiResponse.ok(buildImportResponse(saved, "IMPORTED"));
    }

    @PostMapping("/{id}/refresh")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<Map<String, Object>> refresh(@AuthenticationPrincipal CodeKbPrincipal principal,
                                                    @PathVariable Long id) {
        KbRepo refreshed = repoService.refreshRepo(principal.userId(), id);
        analysisService.rebuild(id);
        return ApiResponse.ok(buildImportResponse(refreshed, "UPDATED"));
    }

    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> get(@AuthenticationPrincipal CodeKbPrincipal principal,
                                                @PathVariable Long id) {
        KbRepo repo = repoService.getOwnedById(principal.userId(), id);
        var summary = summaryRepository.findByRepoId(id).orElse(null);
        var tasks = graphTaskRepository.findByRepoIdOrderByCreatedAtDesc(id);
        var latestGraphTask = repoService.currentLatestGraphTask(repo, tasks);
        Map<String, Object> result = new HashMap<>();
        result.put("repo", repoService.toRepoView(repo, summary, latestGraphTask));
        result.put("summary", summary);
        result.put("latestGraphTask", latestGraphTask);
        return ApiResponse.ok(result);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal CodeKbPrincipal principal,
                                    @PathVariable Long id) {
        repoService.deleteRepo(principal.userId(), id);
        return ApiResponse.ok();
    }

    private Map<String, Object> buildImportResponse(KbRepo repo, String action) {
        boolean hasSummary = summaryRepository.findByRepoId(repo.getId()).isPresent();
        Map<String, Object> resp = new HashMap<>();
        resp.put("repoId", repo.getId());
        resp.put("kbId", repo.getKbId());
        resp.put("repoName", repo.getName());
        resp.put("status", repoService.normalizeRepoStatus(repo.getStatus(), hasSummary));
        resp.put("action", action);
        return resp;
    }

    record ImportRequest(@NotNull Long kbId,
                         @NotBlank String githubUrl,
                         String provider,
                         String repoName,
                         String ref,
                         Integer depth) {}
}
