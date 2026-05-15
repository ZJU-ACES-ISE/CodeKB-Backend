package com.codekb.knowledge;

import com.codekb.auth.CodeKbPrincipal;
import com.codekb.common.ApiResponse;
import com.codekb.repo.KbRepoService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/knowledge-bases")
public class KnowledgeBaseController {

    private final KnowledgeBaseService kbService;
    private final KbRepoService repoService;

    public KnowledgeBaseController(KnowledgeBaseService kbService, KbRepoService repoService) {
        this.kbService = kbService;
        this.repoService = repoService;
    }

    @GetMapping
    public ApiResponse<List<KnowledgeBase>> list(@AuthenticationPrincipal CodeKbPrincipal principal) {
        return ApiResponse.ok(kbService.listByOwner(principal.userId()));
    }

    @PostMapping
    public ApiResponse<KnowledgeBase> create(@AuthenticationPrincipal CodeKbPrincipal principal,
                                              @RequestBody @Valid CreateRequest req) {
        return ApiResponse.ok(kbService.create(principal.userId(), req.name(), req.description()));
    }

    @GetMapping("/{id}")
    public ApiResponse<KnowledgeBase> get(@PathVariable Long id) {
        return ApiResponse.ok(kbService.getById(id));
    }

    @PutMapping("/{id}")
    public ApiResponse<KnowledgeBase> update(@AuthenticationPrincipal CodeKbPrincipal principal,
                                             @PathVariable Long id,
                                             @RequestBody @Valid UpdateRequest req) {
        return ApiResponse.ok(kbService.update(principal.userId(), id, req.name(), req.description()));
    }

    @GetMapping("/{id}/repos")
    public ApiResponse<?> listRepos(@PathVariable Long id) {
        kbService.getById(id); // 404 guard
        return ApiResponse.ok(repoService.listRepoViewsByKb(id));
    }

    record CreateRequest(@NotBlank String name, String description) {}

    record UpdateRequest(@NotBlank String name, String description) {}
}
