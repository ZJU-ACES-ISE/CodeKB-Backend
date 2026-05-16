package com.codekb.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "codekb.github")
public class GithubApiProperties {

    private String token;

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String resolveToken() {
        return firstNonBlank(
                token,
                System.getenv("CODEKB_GITHUB_TOKEN"),
                System.getenv("GITHUB_TOKEN"),
                System.getenv("GH_TOKEN")
        );
    }

    public boolean hasToken() {
        return !resolveToken().isBlank();
    }

    public String tokenSource() {
        if (hasText(token)) {
            return "codekb.github.token";
        }
        if (hasText(System.getenv("CODEKB_GITHUB_TOKEN"))) {
            return "CODEKB_GITHUB_TOKEN";
        }
        if (hasText(System.getenv("GITHUB_TOKEN"))) {
            return "GITHUB_TOKEN";
        }
        if (hasText(System.getenv("GH_TOKEN"))) {
            return "GH_TOKEN";
        }
        return "unconfigured";
    }

    private String firstNonBlank(String... candidates) {
        for (String candidate : candidates) {
            if (hasText(candidate)) {
                return candidate.trim();
            }
        }
        return "";
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
