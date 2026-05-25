package com.codekb.graph;

import com.codekb.auth.CodeKbPrincipal;
import com.codekb.common.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
public class GraphController {

    private final GraphService graphService;
    public GraphController(GraphService graphService) {
        this.graphService = graphService;
    }

    @PostMapping("/graph-jobs")
    public ApiResponse<RepoGraphTask> create(@AuthenticationPrincipal CodeKbPrincipal principal,
                                             @RequestBody @Valid CreateTaskRequest req) {
        return ApiResponse.ok(graphService.createTask(principal.userId(), req.repoId(), req.ref(), req.depth()));
    }

    @GetMapping("/graph-jobs")
    public ApiResponse<List<RepoGraphTask>> listByRepo(@AuthenticationPrincipal CodeKbPrincipal principal,
                                                       @RequestParam Long repoId) {
        return ApiResponse.ok(graphService.listTasksByRepo(principal.userId(), repoId));
    }

    @GetMapping("/graph-jobs/{taskId}")
    public ApiResponse<RepoGraphTask> getTask(@AuthenticationPrincipal CodeKbPrincipal principal,
                                              @PathVariable Long taskId) {
        return ApiResponse.ok(graphService.getTask(principal.userId(), taskId));
    }

    @GetMapping("/graph-jobs/{taskId}/graph")
    public ApiResponse<Map<String, Object>> getGraph(@AuthenticationPrincipal CodeKbPrincipal principal,
                                                     @PathVariable Long taskId) {
        return ApiResponse.ok(graphService.getGraph(principal.userId(), taskId));
    }

    @GetMapping("/graph/repos/{repoId}/latest")
    public ApiResponse<Map<String, Object>> getLatest(@AuthenticationPrincipal CodeKbPrincipal principal,
                                                      @PathVariable Long repoId) {
        return ApiResponse.ok(graphService.getLatestReadyGraph(principal.userId(), repoId));
    }

    /** 查询最新图任务的元数据（不限定 READY，供前端展示当前状态） */
    @GetMapping("/graph/repos/{repoId}/latest-task")
    public ApiResponse<RepoGraphTask> getLatestTask(@AuthenticationPrincipal CodeKbPrincipal principal,
                                                    @PathVariable Long repoId) {
        return ApiResponse.ok(graphService.getLatestTask(principal.userId(), repoId));
    }

    record CreateTaskRequest(@NotNull Long repoId, String ref, Integer depth) {}
}
