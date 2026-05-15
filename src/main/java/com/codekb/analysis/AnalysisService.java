package com.codekb.analysis;

import com.codekb.event.GraphJobRequestedEvent;
import com.codekb.event.RepoImportedEvent;
import com.codekb.repo.KbRepo;
import com.codekb.repo.KbRepoService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.*;

@Service
public class AnalysisService {

    private static final Logger log = LoggerFactory.getLogger(AnalysisService.class);

    private final KbRepoService repoService;
    private final RepoSummaryRepository summaryRepo;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public AnalysisService(KbRepoService repoService,
                           RepoSummaryRepository summaryRepo,
                           ApplicationEventPublisher eventPublisher,
                           ObjectMapper objectMapper) {
        this.repoService = repoService;
        this.summaryRepo = summaryRepo;
        this.eventPublisher = eventPublisher;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder()
                .defaultHeader(HttpHeaders.USER_AGENT, "CodeKB-Demo/0.1")
                .defaultHeader(HttpHeaders.ACCEPT, "application/vnd.github+json")
                .build();
    }

    @EventListener
    public void handleRepoImported(RepoImportedEvent event) {
        Long repoId = event.getRepoId();
        log.info("Analysis triggered for repoId={}", repoId);
        try {
            KbRepo repo = repoService.getById(repoId);
            RepoSummary summary = summaryRepo.findByRepoId(repoId).orElse(new RepoSummary());
            summary.setRepoId(repoId);

            String language = "Unknown";
            String description = "";
            int starCount = 0, forkCount = 0, watchers = 0, openIssues = 0, sizeKb = 0;
            String license = null, defaultBranch = null, homepage = null;
            List<String> topics = new ArrayList<>();
            Map<String, Integer> languageBytes = new LinkedHashMap<>();
            List<Map<String, Object>> contributors = new ArrayList<>();

            if (repo.getOwner() != null && repo.getRepo() != null) {
                String base = "https://api.github.com/repos/" + repo.getOwner() + "/" + repo.getRepo();

                // 1) main repo info
                try {
                    String body = restClient.get().uri(base).retrieve().body(String.class);
                    JsonNode node = objectMapper.readTree(body);
                    if (!node.path("language").isNull()) language = node.path("language").asText("Unknown");
                    description = node.path("description").asText("");
                    starCount = node.path("stargazers_count").asInt(0);
                    forkCount = node.path("forks_count").asInt(0);
                    watchers = node.path("watchers_count").asInt(0);
                    openIssues = node.path("open_issues_count").asInt(0);
                    sizeKb = node.path("size").asInt(0);
                    defaultBranch = nullableText(node, "default_branch");
                    homepage = nullableText(node, "homepage");
                    JsonNode licNode = node.path("license");
                    if (licNode != null && !licNode.isMissingNode() && !licNode.isNull()) {
                        license = nullableText(licNode, "spdx_id");
                    }
                    JsonNode topicsNode = node.path("topics");
                    if (topicsNode.isArray()) topicsNode.forEach(t -> topics.add(t.asText()));
                } catch (Exception e) {
                    log.warn("GitHub repo info failed for {}/{}: {}", repo.getOwner(), repo.getRepo(), e.getMessage());
                }

                // 2) languages distribution
                try {
                    String body = restClient.get().uri(base + "/languages").retrieve().body(String.class);
                    JsonNode node = objectMapper.readTree(body);
                    node.fieldNames().forEachRemaining(f -> languageBytes.put(f, node.path(f).asInt(0)));
                } catch (Exception e) {
                    log.warn("GitHub languages failed: {}", e.getMessage());
                }

                // 3) top contributors
                try {
                    String body = restClient.get().uri(base + "/contributors?per_page=10").retrieve().body(String.class);
                    JsonNode arr = objectMapper.readTree(body);
                    if (arr.isArray()) {
                        for (JsonNode c : arr) {
                            Map<String, Object> m = new LinkedHashMap<>();
                            m.put("login", c.path("login").asText(""));
                            m.put("avatar_url", c.path("avatar_url").asText(""));
                            m.put("html_url", c.path("html_url").asText(""));
                            m.put("contributions", c.path("contributions").asInt(0));
                            contributors.add(m);
                        }
                    }
                } catch (Exception e) {
                    log.warn("GitHub contributors failed: {}", e.getMessage());
                }
            }

            List<String> frameworks = FrameworkInference.labelsFromTopics(topics);
            List<String> tags = deriveTags(topics, language);

            summary.setPrimaryLanguage(language);
            summary.setDescription(description);
            summary.setFrameworks(objectMapper.writeValueAsString(frameworks));
            summary.setTags(objectMapper.writeValueAsString(tags));
            summary.setTopics(objectMapper.writeValueAsString(topics));
            summary.setLanguages(objectMapper.writeValueAsString(toLanguagePercent(languageBytes)));
            summary.setTopContributors(objectMapper.writeValueAsString(contributors));
            summary.setContributorCount(contributors.size());
            summary.setForkCount(forkCount);
            summary.setWatchersCount(watchers);
            summary.setOpenIssuesCount(openIssues);
            summary.setLicense(license);
            summary.setDefaultBranch(defaultBranch);
            summary.setHomepage(homepage);
            summary.setSizeKb(sizeKb);
            // demo 阶段 file_count / code_line_count 没有真实统计，留 0
            summary.setFileCount(0);
            summary.setCodeLineCount(0);

            summary.setSummaryText(buildSummaryText(
                    repo.getName(), language, description, topics, frameworks,
                    starCount, forkCount, watchers, openIssues, contributors.size(),
                    sizeKb, license, defaultBranch));

            summaryRepo.save(summary);
            repoService.updateLanguageAndStar(repoId, language, starCount);
            repoService.updateStatus(repoId, "SUMMARIZED");
            log.info("Summary saved for repoId={} lang={} topics={} contributors={}",
                    repoId, language, topics.size(), contributors.size());

            String gh = repo.getGithubUrl();
            if (gh == null || !gh.startsWith("upload://")) {
                eventPublisher.publishEvent(new GraphJobRequestedEvent(this, repoId));
            }
        } catch (Exception e) {
            log.error("Analysis failed for repoId={}: {}", repoId, e.getMessage(), e);
            repoService.updateStatus(repoId, "FAILED");
        }
    }

    private List<String> deriveTags(List<String> topics, String language) {
        Set<String> tags = new LinkedHashSet<>();
        if (language != null && !"Unknown".equals(language)) tags.add(language.toLowerCase());
        for (String t : topics) {
            if (tags.size() >= 8) break;
            tags.add(t);
        }
        if (tags.isEmpty()) tags.add("open-source");
        return new ArrayList<>(tags);
    }

    private Map<String, Double> toLanguagePercent(Map<String, Integer> bytes) {
        long total = bytes.values().stream().mapToLong(Integer::longValue).sum();
        if (total == 0) return Map.of();
        Map<String, Double> result = new LinkedHashMap<>();
        bytes.forEach((k, v) -> result.put(k, Math.round((v * 1000.0) / total) / 10.0));
        return result;
    }

    private String buildSummaryText(String repoName, String language, String description,
                                    List<String> topics, List<String> frameworks,
                                    int stars, int forks, int watchers, int issues,
                                    int contributorCount, int sizeKb,
                                    String license, String branch) {
        StringBuilder sb = new StringBuilder();
        sb.append(repoName).append(" 是一个");
        if (language != null && !"Unknown".equals(language)) sb.append("以 ").append(language).append(" 为主的");
        sb.append("开源项目");
        if (description != null && !description.isBlank()) {
            sb.append("。简介：").append(description.length() > 160 ? description.substring(0, 160) + "…" : description);
        }
        if (!frameworks.isEmpty()) sb.append("。主要使用 ").append(String.join("、", frameworks)).append("");
        if (!topics.isEmpty()) {
            List<String> shown = topics.subList(0, Math.min(6, topics.size()));
            sb.append("，涉及 ").append(String.join(" / ", shown)).append(" 等领域");
        }
        if (stars > 0 || forks > 0) {
            sb.append("。社区关注度 ⭐ ").append(stars);
            if (forks > 0) sb.append("，🍴 ").append(forks);
            if (watchers > 0 && watchers != stars) sb.append("，👀 ").append(watchers);
            if (issues > 0) sb.append("，待处理 issue ").append(issues).append(" 个");
        }
        if (contributorCount > 0) sb.append("。共有 ").append(contributorCount).append(" 位主要贡献者");
        if (sizeKb > 0) sb.append("，代码规模约 ").append(formatSize(sizeKb));
        if (license != null) sb.append("，许可证 ").append(license);
        if (branch != null) sb.append("，默认分支 ").append(branch);
        sb.append("。");
        return sb.toString();
    }

    private String formatSize(int kb) {
        if (kb >= 1024) return String.format("%.1f MB", kb / 1024.0);
        return kb + " KB";
    }

    private String nullableText(JsonNode node, String field) {
        JsonNode v = node.path(field);
        if (v.isMissingNode() || v.isNull()) return null;
        String s = v.asText("");
        return s.isBlank() ? null : s;
    }
}
