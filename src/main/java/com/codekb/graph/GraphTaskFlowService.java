package com.codekb.graph;

import com.codekb.analysis.RepoSummary;
import com.codekb.analysis.RepoSummaryRepository;
import com.codekb.knowledge.KnowledgeBase;
import com.codekb.knowledge.KnowledgeBaseRepository;
import com.codekb.repo.KbRepo;
import com.codekb.repo.KbRepoService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class GraphTaskFlowService {

    private final RepoSummaryRepository summaryRepository;
    private final RepoGraphTaskRepository graphTaskRepository;
    private final KnowledgeBaseRepository kbRepository;
    private final KbRepoService repoService;

    public GraphTaskFlowService(RepoSummaryRepository summaryRepository,
                                RepoGraphTaskRepository graphTaskRepository,
                                KnowledgeBaseRepository kbRepository,
                                KbRepoService repoService) {
        this.summaryRepository = summaryRepository;
        this.graphTaskRepository = graphTaskRepository;
        this.kbRepository = kbRepository;
        this.repoService = repoService;
    }

    public List<Map<String, Object>> listFlows(Long userId) {
        List<KbRepo> repos = new ArrayList<>(repoService.listOwnedRepos(userId));
        if (repos.isEmpty()) {
            return List.of();
        }

        List<Long> repoIds = repos.stream().map(KbRepo::getId).toList();
        Map<Long, RepoSummary> summariesByRepo = summaryRepository.findByRepoIdIn(repoIds).stream()
                .collect(Collectors.toMap(RepoSummary::getRepoId, summary -> summary, (left, right) -> left, LinkedHashMap::new));

        List<RepoGraphTask> allTasks = graphTaskRepository.findByRepoIdInOrderByRepoIdAscCreatedAtDesc(repoIds);
        Map<Long, List<RepoGraphTask>> tasksByRepo = new HashMap<>();
        Map<Long, Integer> taskCounts = new HashMap<>();
        for (RepoGraphTask task : allTasks) {
            tasksByRepo.computeIfAbsent(task.getRepoId(), ignored -> new ArrayList<>())
                    .add(task);
            taskCounts.merge(task.getRepoId(), 1, Integer::sum);
        }

        Map<Long, String> kbNames = kbNameMap(repos);

        repos.sort(Comparator
                .comparing((KbRepo repo) -> latestActivityAt(
                                repo,
                                summariesByRepo.get(repo.getId()),
                                repoService.currentLatestGraphTask(repo, tasksByRepo.getOrDefault(repo.getId(), List.of()))),
                        Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(KbRepo::getId, Comparator.reverseOrder()));

        List<Map<String, Object>> result = new ArrayList<>(repos.size());
        for (KbRepo repo : repos) {
            RepoSummary summary = summariesByRepo.get(repo.getId());
            RepoGraphTask latestTask = repoService.currentLatestGraphTask(
                    repo, tasksByRepo.getOrDefault(repo.getId(), List.of()));
            String repoStatus = repoService.normalizeRepoStatus(repo.getStatus(), summary != null);
            StageInfo stage = currentStage(repoStatus, summary, latestTask);
            LocalDateTime latestActivityAt = latestActivityAt(repo, summary, latestTask);

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("repo", repoService.toRepoView(repo, summary, latestTask));
            item.put("kbName", kbNames.getOrDefault(repo.getKbId(), "\u77e5\u8bc6\u5e93 #" + repo.getKbId()));
            item.put("summaryExists", summary != null);
            item.put("summaryCreatedAt", summary != null ? summary.getCreatedAt() : null);
            item.put("summaryUpdatedAt", summary != null ? summary.getUpdatedAt() : null);
            item.put("latestGraphTask", latestTask);
            item.put("graphTaskCount", taskCounts.getOrDefault(repo.getId(), 0));
            item.put("latestActivityAt", latestActivityAt);
            item.put("currentStage", stage.code());
            item.put("currentStageLabel", stage.label());
            item.put("errorMessage", stage.errorMessage());
            result.add(item);
        }
        return result;
    }

    public Map<String, Object> getFlow(Long userId, Long repoId) {
        KbRepo repo = repoService.getOwnedById(userId, repoId);
        RepoSummary summary = summaryRepository.findByRepoId(repoId).orElse(null);
        List<RepoGraphTask> tasks = graphTaskRepository.findByRepoIdOrderByCreatedAtDesc(repoId);
        RepoGraphTask latestTask = repoService.currentLatestGraphTask(repo, tasks);
        String repoStatus = repoService.normalizeRepoStatus(repo.getStatus(), summary != null);
        StageInfo stage = currentStage(repoStatus, summary, latestTask);

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("repo", repoService.toRepoView(repo, summary, latestTask));
        item.put("kbName", kbRepository.findById(repo.getKbId()).map(KnowledgeBase::getName).orElse("\u77e5\u8bc6\u5e93 #" + repo.getKbId()));
        item.put("summaryExists", summary != null);
        item.put("summaryCreatedAt", summary != null ? summary.getCreatedAt() : null);
        item.put("summaryUpdatedAt", summary != null ? summary.getUpdatedAt() : null);
        item.put("latestGraphTask", latestTask);
        item.put("graphTaskCount", tasks.size());
        item.put("latestActivityAt", latestActivityAt(repo, summary, latestTask));
        item.put("currentStage", stage.code());
        item.put("currentStageLabel", stage.label());
        item.put("errorMessage", stage.errorMessage());
        return item;
    }

    private Map<Long, String> kbNameMap(Collection<KbRepo> repos) {
        List<Long> kbIds = repos.stream().map(KbRepo::getKbId).distinct().toList();
        return kbRepository.findAllById(kbIds).stream()
                .collect(Collectors.toMap(KnowledgeBase::getId, KnowledgeBase::getName, (left, right) -> left, LinkedHashMap::new));
    }

    private LocalDateTime latestActivityAt(KbRepo repo, RepoSummary summary, RepoGraphTask task) {
        LocalDateTime latest = repo.getUpdatedAt() != null ? repo.getUpdatedAt() : repo.getCreatedAt();
        latest = later(latest, summary != null ? summary.getCreatedAt() : null);
        latest = later(latest, summary != null ? summary.getUpdatedAt() : null);
        latest = later(latest, task != null ? task.getCreatedAt() : null);
        latest = later(latest, task != null ? task.getSubmittedAt() : null);
        latest = later(latest, task != null ? task.getCompletedAt() : null);
        latest = later(latest, task != null ? task.getUpdatedAt() : null);
        return latest;
    }

    private LocalDateTime later(LocalDateTime left, LocalDateTime right) {
        if (left == null) return right;
        if (right == null) return left;
        return right.isAfter(left) ? right : left;
    }

    private StageInfo currentStage(String repoStatus, RepoSummary summary, RepoGraphTask latestTask) {
        if (latestTask != null && latestTask.getStatus() != null) {
            return switch (latestTask.getStatus()) {
                case READY -> new StageInfo("GRAPH_READY", "\u5173\u8054\u56fe\u5df2\u5b8c\u6210", latestTask.getErrorMessage());
                case FAILED -> new StageInfo("GRAPH_FAILED", "\u5173\u8054\u56fe\u5931\u8d25", latestTask.getErrorMessage());
                case BUILDING -> new StageInfo("GRAPH_BUILDING", "\u5173\u8054\u56fe\u6784\u5efa\u4e2d", latestTask.getErrorMessage());
                case SUBMITTED -> new StageInfo("GRAPH_SUBMITTED", "\u56fe\u4efb\u52a1\u5df2\u63d0\u4ea4", latestTask.getErrorMessage());
                case PENDING -> new StageInfo("GRAPH_PENDING", "\u56fe\u4efb\u52a1\u6392\u961f\u4e2d", latestTask.getErrorMessage());
            };
        }

        if ("FAILED".equals(repoStatus)) {
            return new StageInfo("SUMMARY_FAILED", "\u6458\u8981\u89e3\u6790\u5931\u8d25", null);
        }
        if (summary != null) {
            return new StageInfo("SUMMARY_READY", "\u6458\u8981\u89e3\u6790\u5b8c\u6210", null);
        }
        return new StageInfo("REPO_IMPORTED", "\u4ed3\u5e93\u5df2\u5bfc\u5165", null);
    }

    private record StageInfo(String code, String label, String errorMessage) {}
}
