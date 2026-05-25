package com.codekb.analysis;

import com.codekb.auth.CodeKbPrincipal;
import com.codekb.common.ApiResponse;
import com.codekb.common.BusinessException;
import com.codekb.repo.KbRepoService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/analysis")
public class AnalysisController {

    private final RepoSummaryRepository summaryRepo;
    private final AnalysisService analysisService;
    private final KbRepoService repoService;

    public AnalysisController(RepoSummaryRepository summaryRepo,
                              AnalysisService analysisService,
                              KbRepoService repoService) {
        this.summaryRepo = summaryRepo;
        this.analysisService = analysisService;
        this.repoService = repoService;
    }

    @GetMapping("/repos/{repoId}/summary")
    public ApiResponse<RepoSummary> getSummary(@AuthenticationPrincipal CodeKbPrincipal principal,
                                               @PathVariable Long repoId) {
        repoService.getOwnedById(principal.userId(), repoId);
        return ApiResponse.ok(summaryRepo.findByRepoId(repoId)
                .orElseThrow(() -> new BusinessException(404, "摘要不存在，可能解析尚未完成")));
    }

    @PostMapping("/repos/{repoId}/retry")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<Map<String, Object>> retry(@AuthenticationPrincipal CodeKbPrincipal principal,
                                                  @PathVariable Long repoId) {
        repoService.getOwnedById(principal.userId(), repoId);
        analysisService.reanalyze(repoId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("repoId", repoId);
        result.put("status", "IMPORTED");
        return ApiResponse.ok(result);
    }
}
