package com.codekb.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "codekb.cors")
public class CorsProperties {

    private List<String> allowedOriginPatterns = new ArrayList<>(List.of(
            "http://localhost:*",
            "https://localhost:*",
            "http://*.graywolf.top",
            "https://*.graywolf.top"
    ));

    public List<String> getAllowedOriginPatterns() {
        return allowedOriginPatterns;
    }

    public void setAllowedOriginPatterns(List<String> allowedOriginPatterns) {
        this.allowedOriginPatterns = allowedOriginPatterns != null ? new ArrayList<>(allowedOriginPatterns) : new ArrayList<>();
    }
}
