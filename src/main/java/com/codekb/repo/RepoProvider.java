package com.codekb.repo;

import java.util.Locale;

public enum RepoProvider {
    GITHUB("github"),
    GITEE("gitee"),
    GITLAB("gitlab"),
    LOCAL("local"),
    ZIP("zip"),
    OTHER("other");

    private final String key;

    RepoProvider(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public static RepoProvider fromNullable(String value) {
        if (value == null || value.isBlank()) {
            return OTHER;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "github" -> GITHUB;
            case "gitee" -> GITEE;
            case "gitlab" -> GITLAB;
            case "local" -> LOCAL;
            case "zip", "upload" -> ZIP;
            default -> OTHER;
        };
    }
}
