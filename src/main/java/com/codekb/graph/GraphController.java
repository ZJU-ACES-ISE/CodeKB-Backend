package com.codekb.graph;

import com.codekb.common.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
public class GraphController {

    private final GraphService graphService;
    private final RepoGraphTaskRepository taskRepo;

    public GraphController(GraphService graphService,
                           RepoGraphTaskRepository taskRepo) {
        this.graphService = graphService;
        this.taskRepo = taskRepo;
    }

    @PostMapping("/graph-jobs")
    public ApiResponse<RepoGraphTask> create(@RequestBody @Valid CreateTaskRequest req) {
        return ApiResponse.ok(graphService.createTask(req.repoId(), req.ref(), req.depth()));
    }

    @GetMapping("/graph-jobs/{taskId}")
    public ApiResponse<RepoGraphTask> getTask(@PathVariable Long taskId) {
        return ApiResponse.ok(graphService.getTask(taskId));
    }

    @GetMapping("/graph-jobs/{taskId}/graph")
    public ApiResponse<Map<String, Object>> getGraph(@PathVariable Long taskId) {
        return ApiResponse.ok(graphService.getGraph(taskId));
    }

    @GetMapping("/graph/repos/{repoId}/latest")
    public ApiResponse<Map<String, Object>> getLatest(@PathVariable Long repoId) {
        return ApiResponse.ok(graphService.getLatestReadyGraph(repoId));
    }

    /** 查询最新 READY 图任务的元数据（用于前端显示 Job ID / 状态） */
    @GetMapping("/graph/repos/{repoId}/latest-task")
    public ApiResponse<RepoGraphTask> getLatestTask(@PathVariable Long repoId) {
        return ApiResponse.ok(taskRepo
                .findFirstByRepoIdAndStatusOrderByCreatedAtDesc(repoId, com.codekb.graph.GraphTaskStatus.READY)
                .orElseThrow(() -> new com.codekb.common.BusinessException(404, "没有就绪的图任务")));
    }

    record CreateTaskRequest(@NotNull Long repoId, String ref, Integer depth) {}
}
