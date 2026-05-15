package com.codekb.search;

import com.codekb.analysis.FrameworkInference;
import com.codekb.analysis.RepoSummary;
import com.codekb.analysis.RepoSummaryRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.codekb.common.ApiResponse;
import com.codekb.graph.RepoGraphTask;
import com.codekb.graph.RepoGraphTaskRepository;
import com.codekb.knowledge.KnowledgeBase;
import com.codekb.knowledge.KnowledgeBaseRepository;
import com.codekb.repo.KbRepo;
import com.codekb.repo.KbRepoRepository;
import com.codekb.repo.RepoUrlParser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/search")
public class SearchController {

    private final KbRepoRepository repoRepository;
    private final RepoSummaryRepository summaryRepository;
    private final KnowledgeBaseRepository kbRepository;
    private final RepoGraphTaskRepository graphTaskRepository;
    private final ObjectMapper objectMapper;

    public SearchController(KbRepoRepository repoRepository,
                            RepoSummaryRepository summaryRepository,
                            KnowledgeBaseRepository kbRepository,
                            RepoGraphTaskRepository graphTaskRepository,
                            ObjectMapper objectMapper) {
        this.repoRepository = repoRepository;
        this.summaryRepository = summaryRepository;
        this.kbRepository = kbRepository;
        this.graphTaskRepository = graphTaskRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * 代码检索：在仓库名、描述、摘要文本、tags、topics、语言中进行关键词模糊匹配。
     * 返回匹配的「代码片段」列表（从 summaryText 中截取高亮段落，模拟代码片段结果）。
     */
    @GetMapping("/code")
    public ApiResponse<List<Map<String, Object>>> searchCode(
            @RequestParam String q,
            @RequestParam(defaultValue = "20") int limit) {
        if (q == null || q.trim().isEmpty()) return ApiResponse.ok(Collections.emptyList());
        String kw = q.trim().toLowerCase();
        List<Map<String, Object>> results = new ArrayList<>();

        List<KbRepo> repos = repoRepository.findAll();
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

            // 从 summaryText 中截取高亮摘要段
            String snippet = buildSnippet(sumOpt.orElse(null), kw);
            item.put("snippet", snippet);

            // 补充 topics / frameworks
            if (sumOpt.isPresent()) {
                item.put("topics", sumOpt.get().getTopics());
                item.put("frameworks", sumOpt.get().getFrameworks());
                item.put("description", sumOpt.get().getDescription());
            }

            // mock 代码片段（从节点中找）—— 真实场景应查 ES/code_symbol
            item.put("codeSnippets", mockCodeSnippets(repo, kw));

            results.add(item);
            if (results.size() >= limit) break;
        }

        results.sort(Comparator.comparingDouble(m -> -((Number) ((Map<?, ?>) m).get("score")).doubleValue()));
        return ApiResponse.ok(results);
    }

    /**
     * 资产统计：供「公司资产」页消费。
     */
    @GetMapping("/stats")
    public ApiResponse<Map<String, Object>> stats() {
        List<KnowledgeBase> kbs = kbRepository.findAll();
        List<KbRepo> repos = repoRepository.findAll();
        List<RepoSummary> summaries = summaryRepository.findAll();

        // 语言分布
        Map<String, Long> langDist = repos.stream()
                .filter(r -> r.getLanguage() != null && !r.getLanguage().isBlank())
                .collect(Collectors.groupingBy(KbRepo::getLanguage, Collectors.counting()));

        // 状态分布
        Map<String, Long> statusDist = repos.stream()
                .collect(Collectors.groupingBy(KbRepo::getStatus, Collectors.counting()));
        Map<String, Long> providerDist = repos.stream()
                .collect(Collectors.groupingBy(this::providerOf, Collectors.counting()));

        // Star 排行（前 10）
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

        // 领域汇总：从 topics 归类后聚合，减少语义重复
        Map<String, Long> topicDist = new LinkedHashMap<>();
        for (RepoSummary s : summaries) {
            List<String> topics = FrameworkInference.parseJsonStringArray(s.getTopics(), objectMapper);
            LinkedHashSet<String> domains = new LinkedHashSet<>();
            // 优先归类映射
            domains.addAll(FrameworkInference.domainsFromTopics(topics));
            // 未归类的 topic 也保留（已去掉被映射过的）
            for (String t : topics) {
                if (FrameworkInference.mapTopicToDomain(t) == null && !t.isBlank()) {
                    domains.add(t);
                }
            }
            for (String domain : domains) {
                topicDist.merge(domain, 1L, Long::sum);
            }
        }

        // 总节点/边数（从已完成的图任务）
        long totalNodes = 0, totalEdges = 0;
        for (KbRepo r : repos) {
            Optional<RepoGraphTask> task = graphTaskRepository
                    .findFirstByRepoIdAndStatusOrderByCreatedAtDesc(r.getId(), com.codekb.graph.GraphTaskStatus.READY);
            if (task.isPresent()) {
                totalNodes += task.get().getNodeCount() != null ? task.get().getNodeCount() : 0;
                totalEdges += task.get().getEdgeCount() != null ? task.get().getEdgeCount() : 0;
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("kbCount", kbs.size());
        result.put("repoCount", repos.size());
        result.put("graphReadyCount", statusDist.getOrDefault("GRAPH_READY", 0L));
        result.put("totalNodes", totalNodes);
        result.put("totalEdges", totalEdges);
        result.put("languageDistribution", sortedByValue(langDist));
        result.put("providerDistribution", sortedByValue(providerDist));
        result.put("statusDistribution", statusDist);
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

    // ---- helpers ----

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
        if (idx < 0) return src.length() > 120 ? src.substring(0, 120) + "…" : src;
        int start = Math.max(0, idx - 40);
        int end = Math.min(src.length(), idx + kw.length() + 80);
        return (start > 0 ? "…" : "") + src.substring(start, end) + (end < src.length() ? "…" : "");
    }

    private List<Map<String, Object>> mockCodeSnippets(KbRepo repo, String kw) {
        // 模拟代码片段：demo 阶段没有真实代码索引，用仓库信息构造示意
        List<Map<String, Object>> snippets = new ArrayList<>();
        String lang = repo.getLanguage() != null ? repo.getLanguage() : "Unknown";
        String ext = langExt(lang);
        Map<String, Object> s1 = new LinkedHashMap<>();
        s1.put("file", "src/main/" + kw.replaceAll("\\s+", "_") + ext);
        s1.put("startLine", 1);
        s1.put("endLine", 8);
        s1.put("code", "// " + repo.getName() + " — mock snippet for \"" + kw + "\"\n" +
                "// 真实代码索引需接入 Tree-sitter 解析结果\n" +
                "// 当前为 demo 模拟展示");
        snippets.add(s1);
        return snippets;
    }

    private String providerOf(KbRepo repo) {
        return RepoUrlParser.parse(repo.getGithubUrl(), repo.getProvider()).provider().key();
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
