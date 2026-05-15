package com.codekb.analysis;

import com.codekb.common.ApiResponse;
import com.codekb.common.BusinessException;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/analysis")
public class AnalysisController {

    private final RepoSummaryRepository summaryRepo;

    public AnalysisController(RepoSummaryRepository summaryRepo) {
        this.summaryRepo = summaryRepo;
    }

    @GetMapping("/repos/{repoId}/summary")
    public ApiResponse<RepoSummary> getSummary(@PathVariable Long repoId) {
        return ApiResponse.ok(summaryRepo.findByRepoId(repoId)
                .orElseThrow(() -> new BusinessException(404, "摘要不存在，可能解析尚未完成")));
    }
}
