package com.codekb.repo;

import com.codekb.analysis.RepoSummaryRepository;
import com.codekb.auth.CodeKbPrincipal;
import com.codekb.common.ApiResponse;
import com.codekb.graph.RepoGraphTaskRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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

    public KbRepoController(KbRepoService repoService,
                             RepoSummaryRepository summaryRepository,
                             RepoGraphTaskRepository graphTaskRepository) {
        this.repoService = repoService;
        this.summaryRepository = summaryRepository;
        this.graphTaskRepository = graphTaskRepository;
    }

    @PostMapping("/import")
    public ApiResponse<Map<String, Object>> importRepo(
            @AuthenticationPrincipal CodeKbPrincipal principal,
            @RequestBody @Valid ImportRequest req) {
        KbRepo saved = repoService.importRepo(
                req.kbId(), req.githubUrl(), req.provider(), req.ref(),
                req.depth() != null ? req.depth() : 1,
                principal.userId());
        Map<String, Object> resp = new HashMap<>();
        resp.put("repoId", saved.getId());
        resp.put("status", saved.getStatus());
        return ApiResponse.ok(resp);
    }

    @PostMapping(value = "/import-zip", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<Map<String, Object>> importZip(
            @AuthenticationPrincipal CodeKbPrincipal principal,
            @RequestPart("file") MultipartFile file,
            @RequestParam("kbId") Long kbId,
            @RequestParam(value = "repo_name", required = false) String repoName)
            throws java.io.IOException {
        KbRepo saved = repoService.importZip(kbId, file, repoName, principal.userId());
        Map<String, Object> resp = new HashMap<>();
        resp.put("repoId", saved.getId());
        resp.put("status", saved.getStatus());
        return ApiResponse.ok(resp);
    }

    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> get(@PathVariable Long id) {
        KbRepo repo = repoService.getById(id);
        var summary = summaryRepository.findByRepoId(id).orElse(null);
        var latestGraphTask = graphTaskRepository.findFirstByRepoIdOrderByCreatedAtDesc(id).orElse(null);
        Map<String, Object> result = new HashMap<>();
        result.put("repo", repoService.toRepoView(repo, summary, latestGraphTask));
        result.put("summary", summary);
        result.put("latestGraphTask", latestGraphTask);
        return ApiResponse.ok(result);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        repoService.deleteRepo(id);
        return ApiResponse.ok();
    }

    record ImportRequest(@NotNull Long kbId,
                         @NotBlank String githubUrl,
                         String provider,
                         String ref,
                         Integer depth) {}
}
