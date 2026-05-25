package com.codekb.search;

import com.codekb.analysis.FrameworkInference;
import com.codekb.analysis.RepoSummary;
import com.codekb.analysis.RepoSummaryRepository;
import com.codekb.auth.CodeKbPrincipal;
import com.codekb.common.ApiResponse;
import com.codekb.graph.RepoGraphTask;
import com.codekb.graph.RepoGraphTaskRepository;
import com.codekb.knowledge.KnowledgeBase;
import com.codekb.knowledge.KnowledgeBaseService;
import com.codekb.repo.KbRepo;
import com.codekb.repo.KbRepoService;
import com.codekb.repo.RepoUrlParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/search")
public class SearchController {

    private final RepoSummaryRepository summaryRepository;
    private final KnowledgeBaseService kbService;
    private final RepoGraphTaskRepository graphTaskRepository;
    private final KbRepoService repoService;
    private final ObjectMapper objectMapper;

    public SearchController(RepoSummaryRepository summaryRepository,
                            KnowledgeBaseService kbService,
                            RepoGraphTaskRepository graphTaskRepository,
                            KbRepoService repoService,
                            ObjectMapper objectMapper) {
        this.summaryRepository = summaryRepository;
        this.kbService = kbService;
        this.graphTaskRepository = graphTaskRepository;
        this.repoService = repoService;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/code")
    public ApiResponse<List<Map<String, Object>>> searchCode(
            @AuthenticationPrincipal CodeKbPrincipal principal,
            @RequestParam String q,
            @RequestParam(defaultValue = "20") int limit) {
        if (q == null || q.trim().isEmpty()) return ApiResponse.ok(Collections.emptyList());
        String kw = q.trim().toLowerCase();
        List<Map<String, Object>> results = new ArrayList<>();

        List<KbRepo> repos = repoService.listOwnedRepos(principal.userId());
        for (KbRepo repo : repos) {
            Optional<RepoSummary> sumOpt = summaryRepository.findByRepoId(repo.getId());
            double score = scoreRepo(repo, sumOpt.orElse(null), kw);
            if (score <= 0) continue;

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("repoId", repo.getId());
            item.put("repoName", repo.getName());
            item.put("githubUrl", repo.getGithubUrl());
            item.put("provider", providerOf(repo));
            item.put("language", repo.getLanguage());
            item.put("starCount", repo.getStarCount());
            item.put("score", score);

            item.put("snippet", buildSnippet(sumOpt.orElse(null), kw));

            if (sumOpt.isPresent()) {
                item.put("topics", sumOpt.get().getTopics());
                item.put("frameworks", sumOpt.get().getFrameworks());
                item.put("description", sumOpt.get().getDescription());
            }

            item.put("codeSnippets", mockCodeSnippets(repo, kw));

            results.add(item);
            if (results.size() >= limit) break;
        }

        results.sort(Comparator.comparingDouble(m -> -((Number) ((Map<?, ?>) m).get("score")).doubleValue()));
        return ApiResponse.ok(results);
    }

    @GetMapping("/stats")
    public ApiResponse<Map<String, Object>> stats(@AuthenticationPrincipal CodeKbPrincipal principal) {
        kbService.ensureDefaultKnowledgeBase(principal.userId());
        List<KnowledgeBase> kbs = kbService.listByOwner(principal.userId());
        List<KbRepo> repos = repoService.listOwnedRepos(principal.userId());
        List<RepoSummary> summaries = repos.isEmpty()
                ? List.of()
                : summaryRepository.findByRepoIdIn(repos.stream().map(KbRepo::getId).toList());
        Map<Long, KbRepo> repoById = repos.stream()
                .collect(Collectors.toMap(KbRepo::getId, repo -> repo, (left, right) -> left, LinkedHashMap::new));

        Map<String, Long> langDist = repos.stream()
                .filter(r -> r.getLanguage() != null && !r.getLanguage().isBlank())
                .collect(Collectors.groupingBy(KbRepo::getLanguage, Collectors.counting()));

        Set<Long> summarizedRepoIds = summaries.stream()
                .map(RepoSummary::getRepoId)
                .collect(Collectors.toSet());

        Map<String, Long> repoStatusDist = repos.stream()
                .collect(Collectors.groupingBy(
                        repo -> repoService.normalizeRepoStatus(repo.getStatus(), summarizedRepoIds.contains(repo.getId())),
                        Collectors.counting()));
        Map<String, Long> providerDist = repos.stream()
                .collect(Collectors.groupingBy(this::providerOf, Collectors.counting()));

        List<Map<String, Object>> topStars = repos.stream()
                .filter(r -> r.getStarCount() != null && r.getStarCount() > 0)
                .sorted(Comparator.comparingInt(r -> -((KbRepo) r).getStarCount()))
                .limit(10)
                .map(r -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("repoId", r.getId());
                    m.put("name", r.getName());
                    m.put("githubUrl", r.getGithubUrl());
                    m.put("provider", providerOf(r));
                    m.put("language", r.getLanguage());
                    m.put("starCount", r.getStarCount());
                    return m;
                })
                .collect(Collectors.toList());

        Map<String, Long> topicDist = buildTopicDistribution(summaries, repoById);

        Map<Long, RepoGraphTask> latestTaskByRepo = latestGraphTasks(repos);
        Map<String, Long> graphTaskDist = new LinkedHashMap<>();
        long totalNodes = 0;
        long totalEdges = 0;
        long graphReadyCount = 0;
        for (KbRepo repo : repos) {
            RepoGraphTask latestTask = latestTaskByRepo.get(repo.getId());
            if (latestTask == null) {
                graphTaskDist.merge("NONE", 1L, Long::sum);
                continue;
            }

            String graphStatus = latestTask.getStatus().name();
            graphTaskDist.merge(graphStatus, 1L, Long::sum);

            if (latestTask.getStatus() == com.codekb.graph.GraphTaskStatus.READY) {
                graphReadyCount += 1;
                totalNodes += latestTask.getNodeCount() != null ? latestTask.getNodeCount() : 0;
                totalEdges += latestTask.getEdgeCount() != null ? latestTask.getEdgeCount() : 0;
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("kbCount", kbs.size());
        result.put("repoCount", repos.size());
        result.put("graphReadyCount", graphReadyCount);
        result.put("totalNodes", totalNodes);
        result.put("totalEdges", totalEdges);
        result.put("languageDistribution", sortedByValue(langDist));
        result.put("providerDistribution", sortedByValue(providerDist));
        result.put("repoStatusDistribution", sortedByValue(repoStatusDist));
        result.put("statusDistribution", sortedByValue(repoStatusDist));
        result.put("graphTaskDistribution", sortedByValue(graphTaskDist));
        result.put("topicDistribution", sortedByValue(topicDist));
        result.put("topStarRepos", topStars);
        result.put("knowledgeBases", kbs.stream().map(kb -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", kb.getId());
            m.put("name", kb.getName());
            m.put("description", kb.getDescription());
            m.put("repoCount", kb.getRepoCount());
            return m;
        }).collect(Collectors.toList()));
        return ApiResponse.ok(result);
    }

    private double scoreRepo(KbRepo repo, RepoSummary sum, String kw) {
        double score = 0;
        if (contains(repo.getName(), kw)) score += 3;
        if (repo.getLanguage() != null && repo.getLanguage().toLowerCase().contains(kw)) score += 1;
        if (sum != null) {
            if (contains(sum.getDescription(), kw)) score += 2;
            if (contains(sum.getSummaryText(), kw)) score += 1.5;
            if (contains(sum.getTopics(), kw)) score += 2;
            if (contains(sum.getTags(), kw)) score += 1;
            if (contains(sum.getFrameworks(), kw)) score += 1;
        }
        return score;
    }

    private boolean contains(String s, String kw) {
        return s != null && s.toLowerCase().contains(kw);
    }

    private String buildSnippet(RepoSummary sum, String kw) {
        if (sum == null) return "";
        String src = sum.getSummaryText() != null ? sum.getSummaryText() : "";
        if (src.isBlank()) src = sum.getDescription() != null ? sum.getDescription() : "";
        int idx = src.toLowerCase().indexOf(kw);
        if (idx < 0) return src.length() > 120 ? src.substring(0, 120) + "..." : src;
        int start = Math.max(0, idx - 40);
        int end = Math.min(src.length(), idx + kw.length() + 80);
        return (start > 0 ? "..." : "") + src.substring(start, end) + (end < src.length() ? "..." : "");
    }

    private List<Map<String, Object>> mockCodeSnippets(KbRepo repo, String kw) {
        List<Map<String, Object>> snippets = new ArrayList<>();
        String lang = repo.getLanguage() != null ? repo.getLanguage() : "Unknown";
        String ext = langExt(lang);
        Map<String, Object> s1 = new LinkedHashMap<>();
        s1.put("file", "src/main/" + kw.replaceAll("\\s+", "_") + ext);
        s1.put("startLine", 1);
        s1.put("endLine", 8);
        s1.put("code", "// " + repo.getName() + " mock snippet for \"" + kw + "\"\n" +
                "// real code search should be backed by a symbol or index service\n" +
                "// current implementation keeps the demo behavior");
        snippets.add(s1);
        return snippets;
    }

    private String providerOf(KbRepo repo) {
        return RepoUrlParser.parse(repo.getGithubUrl(), repo.getProvider()).provider().key();
    }

    private Map<String, Long> buildTopicDistribution(List<RepoSummary> summaries, Map<Long, KbRepo> repoById) {
        Map<String, Long> topicDist = new LinkedHashMap<>();
        for (RepoSummary summary : summaries) {
            for (String label : topicLabelsOf(summary, repoById.get(summary.getRepoId()))) {
                topicDist.merge(label, 1L, Long::sum);
            }
        }
        return topicDist;
    }

    private List<String> topicLabelsOf(RepoSummary summary, KbRepo repo) {
        LinkedHashSet<String> labels = topicLabelsFromTopics(summary.getTopics());
        if (labels.isEmpty()) {
            labels.addAll(FrameworkInference.labelsFromFrameworksJson(summary.getFrameworks(), objectMapper));
        }
        if (labels.isEmpty()) {
            labels.addAll(meaningfulTagLabels(summary.getTags()));
        }
        if (labels.isEmpty()) {
            String language = summary.getPrimaryLanguage();
            if ((language == null || language.isBlank()) && repo != null) {
                language = repo.getLanguage();
            }
            String fallback = FrameworkInference.primaryLanguageFallbackLabel(language);
            if (fallback != null && !fallback.isBlank()) {
                labels.add(fallback);
            }
        }
        return new ArrayList<>(labels);
    }

    private LinkedHashSet<String> topicLabelsFromTopics(String topicsJson) {
        List<String> topics = FrameworkInference.parseJsonStringArray(topicsJson, objectMapper);
        LinkedHashSet<String> labels = new LinkedHashSet<>(FrameworkInference.domainsFromTopics(topics));
        for (String topic : topics) {
            if (FrameworkInference.mapTopicToDomain(topic) == null && topic != null && !topic.isBlank()) {
                labels.add(topic.trim());
            }
        }
        return labels;
    }

    private List<String> meaningfulTagLabels(String tagsJson) {
        List<String> tags = FrameworkInference.parseJsonStringArray(tagsJson, objectMapper).stream()
                .filter(tag -> tag != null && !tag.isBlank())
                .map(String::trim)
                .filter(tag -> !"open-source".equalsIgnoreCase(tag))
                .toList();
        if (tags.isEmpty()) {
            return List.of();
        }

        LinkedHashSet<String> labels = new LinkedHashSet<>(FrameworkInference.domainsFromTopics(tags));
        if (labels.isEmpty()) {
            labels.addAll(FrameworkInference.labelsFromTopics(tags));
        }
        return new ArrayList<>(labels);
    }

    private Map<Long, RepoGraphTask> latestGraphTasks(List<KbRepo> repos) {
        List<Long> repoIds = repos.stream().map(KbRepo::getId).toList();
        Map<Long, RepoGraphTask> latestTasks = new HashMap<>();
        if (repoIds.isEmpty()) {
            return latestTasks;
        }
        for (RepoGraphTask task : graphTaskRepository.findByRepoIdInOrderByRepoIdAscCreatedAtDesc(repoIds)) {
            latestTasks.putIfAbsent(task.getRepoId(), task);
        }
        return latestTasks;
    }

    private String langExt(String lang) {
        return switch (lang.toLowerCase()) {
            case "java" -> ".java";
            case "python" -> ".py";
            case "javascript" -> ".js";
            case "typescript" -> ".ts";
            case "go" -> ".go";
            case "rust" -> ".rs";
            case "kotlin" -> ".kt";
            default -> ".txt";
        };
    }

    private <K> Map<K, Long> sortedByValue(Map<K, Long> map) {
        return map.entrySet().stream()
                .sorted(Map.Entry.<K, Long>comparingByValue().reversed())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                        (a, b) -> a, LinkedHashMap::new));
    }
}
