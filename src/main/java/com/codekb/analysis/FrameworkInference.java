package com.codekb.analysis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Maps repository topic strings (GitHub topics, etc.) to display labels for stats / summaries.
 */
public final class FrameworkInference {

    private FrameworkInference() {}

    private static final Map<String, String> TOPIC_TO_FRAMEWORK = Map.ofEntries(
            Map.entry("spring-boot", "Spring Boot"),
            Map.entry("springboot", "Spring Boot"),
            Map.entry("spring", "Spring"),
            Map.entry("spring-cloud", "Spring Cloud"),
            Map.entry("springcloud", "Spring Cloud"),
            Map.entry("spring-framework", "Spring"),
            Map.entry("react", "React"),
            Map.entry("reactjs", "React"),
            Map.entry("react-native", "React Native"),
            Map.entry("vue", "Vue.js"),
            Map.entry("vuejs", "Vue.js"),
            Map.entry("vue3", "Vue.js"),
            Map.entry("nuxt", "Nuxt.js"),
            Map.entry("nuxtjs", "Nuxt.js"),
            Map.entry("vite", "Vite"),
            Map.entry("angular", "Angular"),
            Map.entry("svelte", "Svelte"),
            Map.entry("django", "Django"),
            Map.entry("flask", "Flask"),
            Map.entry("fastapi", "FastAPI"),
            Map.entry("express", "Express"),
            Map.entry("nestjs", "NestJS"),
            Map.entry("nest", "NestJS"),
            Map.entry("next", "Next.js"),
            Map.entry("nextjs", "Next.js"),
            Map.entry("kubernetes", "Kubernetes"),
            Map.entry("k8s", "Kubernetes"),
            Map.entry("docker", "Docker"),
            Map.entry("electron", "Electron"),
            Map.entry("flutter", "Flutter"),
            Map.entry("android", "Android"),
            Map.entry("ios", "iOS"),
            Map.entry("rails", "Ruby on Rails"),
            Map.entry("ruby-on-rails", "Ruby on Rails"),
            Map.entry("ror", "Ruby on Rails"),
            Map.entry("laravel", "Laravel"),
            Map.entry("symfony", "Symfony"),
            Map.entry("dotnet", ".NET"),
            Map.entry("net", ".NET"),
            Map.entry("aspnet", "ASP.NET"),
            Map.entry("aspnet-core", "ASP.NET Core"),
            Map.entry("dotnet-core", ".NET"),
            Map.entry("blazor", "Blazor"),
            Map.entry("gin", "Gin"),
            Map.entry("echo", "Echo"),
            Map.entry("fiber", "Fiber"),
            Map.entry("terraform", "Terraform"),
            Map.entry("pytorch", "PyTorch"),
            Map.entry("tensorflow", "TensorFlow")
    );

    /** Longest keys first so e.g. {@code spring-boot} wins over {@code spring}. */
    private static final List<Map.Entry<String, String>> TOPIC_ENTRIES_BY_KEY_LEN = TOPIC_TO_FRAMEWORK.entrySet().stream()
            .sorted(Comparator.comparingInt((Map.Entry<String, String> e) -> e.getKey().length()).reversed())
            .collect(Collectors.toList());

    static String normalizeTopicToken(String topic) {
        if (topic == null) return "";
        return topic.toLowerCase(Locale.ROOT).trim()
                .replace('_', '-')
                .replaceAll("\\s+", "-")
                .replaceAll("-+", "-");
    }

    public static String inferFrameworkLabel(String topic) {
        String n = normalizeTopicToken(topic);
        if (n.isEmpty()) return null;
        if (TOPIC_TO_FRAMEWORK.containsKey(n)) return TOPIC_TO_FRAMEWORK.get(n);
        for (Map.Entry<String, String> e : TOPIC_ENTRIES_BY_KEY_LEN) {
            String k = e.getKey();
            if (k.length() < 3) continue;
            if (n.contains(k) || k.contains(n)) return e.getValue();
        }
        return null;
    }

    public static List<String> labelsFromTopics(Collection<String> topics) {
        if (topics == null || topics.isEmpty()) return List.of();
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (String t : topics) {
            String label = inferFrameworkLabel(t);
            if (label != null) out.add(label);
        }
        return new ArrayList<>(out);
    }

    public static List<String> parseJsonStringArray(String json, ObjectMapper om) {
        if (json == null || json.isBlank()) return List.of();
        String t = json.trim();
        if ("null".equalsIgnoreCase(t)) return List.of();
        try {
            if (t.startsWith("[")) {
                JsonNode arr = om.readTree(t);
                if (!arr.isArray()) return List.of();
                List<String> result = new ArrayList<>();
                for (JsonNode n : arr) {
                    if (n.isTextual()) {
                        String s = n.asText().trim();
                        if (!s.isBlank()) result.add(s);
                    }
                }
                return result;
            }
        } catch (Exception ignored) {
        }
        return List.of();
    }

    public static List<String> labelsFromTopicsJson(String topicsJson, ObjectMapper om) {
        return labelsFromTopics(parseJsonStringArray(topicsJson, om));
    }

    /** summary.tags 为 JSON 字符串数组，与 topics 相同结构，亦可映出框架类 token。 */
    public static List<String> labelsFromTagsJson(String tagsJson, ObjectMapper om) {
        return labelsFromTopics(parseJsonStringArray(tagsJson, om));
    }

    /** Values stored in repo_summary.frameworks (already display names). */
    public static List<String> labelsFromFrameworksJson(String frameworksJson, ObjectMapper om) {
        return parseJsonStringArray(frameworksJson, om);
    }

    /**
     * 将原始 topic 归类到更宽泛的领域分类，减少语义重复。
     * 未命中映射的 topic 原样保留。
     */
    private static final Map<String, String> TOPIC_TO_DOMAIN = Map.ofEntries(
            // AI / 机器学习
            Map.entry("ai", "AI / 机器学习"),
            Map.entry("artificial-intelligence", "AI / 机器学习"),
            Map.entry("machine-learning", "AI / 机器学习"),
            Map.entry("deep-learning", "AI / 机器学习"),
            Map.entry("ml", "AI / 机器学习"),
            Map.entry("nlp", "AI / 机器学习"),
            Map.entry("natural-language-processing", "AI / 机器学习"),
            Map.entry("llm", "AI / 机器学习"),
            Map.entry("large-language-model", "AI / 机器学习"),
            Map.entry("chatgpt", "AI / 机器学习"),
            Map.entry("gpt", "AI / 机器学习"),
            Map.entry("openai", "AI / 机器学习"),
            Map.entry("pytorch", "AI / 机器学习"),
            Map.entry("tensorflow", "AI / 机器学习"),
            Map.entry("keras", "AI / 机器学习"),
            Map.entry("transformer", "AI / 机器学习"),
            Map.entry("langchain", "AI / 机器学习"),
            Map.entry("reinforcement-learning", "AI / 机器学习"),
            Map.entry("computer-vision", "AI / 机器学习"),
            Map.entry("neural-network", "AI / 机器学习"),
            Map.entry("huggingface", "AI / 机器学习"),
            Map.entry("stable-diffusion", "AI / 机器学习"),
            Map.entry("diffusion", "AI / 机器学习"),

            // 聊天机器人
            Map.entry("chatbot", "聊天机器人"),
            Map.entry("chat", "聊天机器人"),
            Map.entry("bot", "聊天机器人"),
            Map.entry("autoreply", "聊天机器人"),
            Map.entry("wechat", "聊天机器人"),
            Map.entry("itchat", "聊天机器人"),
            Map.entry("tuling", "聊天机器人"),
            Map.entry("wechaty", "聊天机器人"),
            Map.entry("telegram-bot", "聊天机器人"),
            Map.entry("slack-bot", "聊天机器人"),
            Map.entry("discord-bot", "聊天机器人"),

            // Python
            Map.entry("python", "Python"),
            Map.entry("python3", "Python"),
            Map.entry("python2", "Python"),
            Map.entry("flask", "Python"),
            Map.entry("django", "Python"),
            Map.entry("fastapi", "Python"),
            Map.entry("scrapy", "Python"),
            Map.entry("scikit-learn", "Python"),

            // Java / JVM
            Map.entry("java", "Java"),
            Map.entry("spring", "Java"),
            Map.entry("spring-boot", "Java"),
            Map.entry("springboot", "Java"),
            Map.entry("spring-cloud", "Java"),
            Map.entry("springcloud", "Java"),
            Map.entry("kotlin", "Java"),
            Map.entry("android", "Android"),
            Map.entry("gradle", "Java"),
            Map.entry("maven", "Java"),

            // JavaScript / 前端
            Map.entry("javascript", "JavaScript"),
            Map.entry("js", "JavaScript"),
            Map.entry("typescript", "JavaScript"),
            Map.entry("nodejs", "JavaScript"),
            Map.entry("node", "JavaScript"),
            Map.entry("react", "前端框架"),
            Map.entry("reactjs", "前端框架"),
            Map.entry("vue", "前端框架"),
            Map.entry("vuejs", "前端框架"),
            Map.entry("vue3", "前端框架"),
            Map.entry("angular", "前端框架"),
            Map.entry("svelte", "前端框架"),
            Map.entry("nextjs", "前端框架"),
            Map.entry("nuxt", "前端框架"),
            Map.entry("nuxtjs", "前端框架"),
            Map.entry("vite", "前端框架"),
            Map.entry("webpack", "前端框架"),
            Map.entry("electron", "前端框架"),

            // Go
            Map.entry("go", "Go"),
            Map.entry("golang", "Go"),
            Map.entry("gin", "Go"),
            Map.entry("grpc", "Go"),
            Map.entry("protobuf", "Go"),

            // Rust
            Map.entry("rust", "Rust"),
            Map.entry("rustlang", "Rust"),
            Map.entry("wasm", "Rust"),
            Map.entry("webassembly", "Rust"),

            // DevOps / 云原生
            Map.entry("docker", "DevOps / 云原生"),
            Map.entry("kubernetes", "DevOps / 云原生"),
            Map.entry("k8s", "DevOps / 云原生"),
            Map.entry("terraform", "DevOps / 云原生"),
            Map.entry("ci-cd", "DevOps / 云原生"),
            Map.entry("cicd", "DevOps / 云原生"),
            Map.entry("devops", "DevOps / 云原生"),
            Map.entry("jenkins", "DevOps / 云原生"),
            Map.entry("helm", "DevOps / 云原生"),
            Map.entry("ansible", "DevOps / 云原生"),

            // 数据库
            Map.entry("mysql", "数据库"),
            Map.entry("postgresql", "数据库"),
            Map.entry("postgres", "数据库"),
            Map.entry("mongodb", "数据库"),
            Map.entry("redis", "数据库"),
            Map.entry("sqlite", "数据库"),
            Map.entry("elasticsearch", "数据库"),
            Map.entry("database", "数据库"),
            Map.entry("sql", "数据库"),
            Map.entry("orm", "数据库"),

            // 数据工程
            Map.entry("data-engineering", "数据工程"),
            Map.entry("data-science", "数据工程"),
            Map.entry("data-analysis", "数据工程"),
            Map.entry("data-visualization", "数据工程"),
            Map.entry("etl", "数据工程"),
            Map.entry("pandas", "数据工程"),
            Map.entry("spark", "数据工程"),
            Map.entry("hadoop", "数据工程"),
            Map.entry("kafka", "数据工程"),
            Map.entry("airflow", "数据工程"),

            // 安全
            Map.entry("security", "安全"),
            Map.entry("cybersecurity", "安全"),
            Map.entry("penetration-testing", "安全"),
            Map.entry("encryption", "安全"),
            Map.entry("authentication", "安全"),
            Map.entry("oauth", "安全"),
            Map.entry("jwt", "安全"),
            Map.entry("cryptography", "安全"),

            // 移动端
            Map.entry("ios", "iOS"),
            Map.entry("swift", "iOS"),
            Map.entry("flutter", "移动跨平台"),
            Map.entry("react-native", "移动跨平台"),
            Map.entry("dart", "移动跨平台"),

            // 微服务 / 后端架构
            Map.entry("microservices", "微服务"),
            Map.entry("microservice", "微服务"),
            Map.entry("api", "API 服务"),
            Map.entry("rest-api", "API 服务"),
            Map.entry("rest", "API 服务"),
            Map.entry("graphql", "API 服务"),
            Map.entry("openapi", "API 服务"),
            Map.entry("swagger", "API 服务"),

            // Web3 / 区块链
            Map.entry("blockchain", "Web3 / 区块链"),
            Map.entry("web3", "Web3 / 区块链"),
            Map.entry("ethereum", "Web3 / 区块链"),
            Map.entry("solidity", "Web3 / 区块链"),
            Map.entry("smart-contract", "Web3 / 区块链"),
            Map.entry("defi", "Web3 / 区块链"),
            Map.entry("nft", "Web3 / 区块链"),

            // 其他常用归类
            Map.entry("cli", "CLI 工具"),
            Map.entry("command-line", "CLI 工具"),
            Map.entry("automation", "自动化"),
            Map.entry("crawler", "爬虫"),
            Map.entry("spider", "爬虫"),
            Map.entry("scraping", "爬虫"),
            Map.entry("tutorial", "教程"),
            Map.entry("documentation", "教程"),
            Map.entry("testing", "测试"),
            Map.entry("unit-testing", "测试"),
            Map.entry("monitoring", "监控"),
            Map.entry("logging", "日志"),
            Map.entry("open-source", "开源项目")
    );

    public static String mapTopicToDomain(String topic) {
        if (topic == null || topic.isBlank()) return null;
        String key = normalizeTopicToken(topic);
        if (TOPIC_TO_DOMAIN.containsKey(key)) return TOPIC_TO_DOMAIN.get(key);
        return null;
    }

    public static List<String> domainsFromTopics(Collection<String> topics) {
        if (topics == null || topics.isEmpty()) return List.of();
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (String t : topics) {
            String domain = mapTopicToDomain(t);
            if (domain != null) out.add(domain);
        }
        return new ArrayList<>(out);
    }

    /**
     * topic 未命中任何已知框架时的统计回退，避免概览卡始终为空（与「语言分布」互补展示）。
     */
    public static String primaryLanguageFallbackLabel(String language) {
        if (language == null) return null;
        String s = language.trim();
        if (s.isEmpty() || "unknown".equalsIgnoreCase(s)) return null;
        return "主语言 · " + s;
    }
}
