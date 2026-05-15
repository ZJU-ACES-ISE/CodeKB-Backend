package com.codekb.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "codekb.graph")
public class GraphServiceProperties {

    private String baseUrl = "http://localhost:8000";
    private long pollIntervalMs = 1500L;
    private int maxPollTimes = 200;
    private int requestTimeoutMs = 10000;

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public long getPollIntervalMs() {
        return pollIntervalMs;
    }

    public void setPollIntervalMs(long pollIntervalMs) {
        this.pollIntervalMs = pollIntervalMs;
    }

    public int getMaxPollTimes() {
        return maxPollTimes;
    }

    public void setMaxPollTimes(int maxPollTimes) {
        this.maxPollTimes = maxPollTimes;
    }

    public int getRequestTimeoutMs() {
        return requestTimeoutMs;
    }

    public void setRequestTimeoutMs(int requestTimeoutMs) {
        this.requestTimeoutMs = requestTimeoutMs;
    }
}
