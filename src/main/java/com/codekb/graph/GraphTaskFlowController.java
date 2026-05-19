package com.codekb.graph;

import com.codekb.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
public class GraphTaskFlowController {

    private final GraphTaskFlowService graphTaskFlowService;

    public GraphTaskFlowController(GraphTaskFlowService graphTaskFlowService) {
        this.graphTaskFlowService = graphTaskFlowService;
    }

    @GetMapping("/graph-task-flows")
    public ApiResponse<List<Map<String, Object>>> list() {
        return ApiResponse.ok(graphTaskFlowService.listFlows());
    }

    @GetMapping("/graph-task-flows/{repoId}")
    public ApiResponse<Map<String, Object>> get(@PathVariable Long repoId) {
        return ApiResponse.ok(graphTaskFlowService.getFlow(repoId));
    }
}
