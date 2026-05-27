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
    private long workerLeaseMs = 10 * 60 * 1000L;
    private long recoveryIntervalMs = 1500L;
    private long staleTaskThresholdMs = 10 * 60 * 1000L;
    private long maxTaskRuntimeMs = 0L;
    private int recoveryBatchSize = 8;

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

    public long getWorkerLeaseMs() {
        return workerLeaseMs;
    }

    public void setWorkerLeaseMs(long workerLeaseMs) {
        this.workerLeaseMs = workerLeaseMs;
    }

    public long getRecoveryIntervalMs() {
        return recoveryIntervalMs;
    }

    public void setRecoveryIntervalMs(long recoveryIntervalMs) {
        this.recoveryIntervalMs = recoveryIntervalMs;
    }

    public long getStaleTaskThresholdMs() {
        return staleTaskThresholdMs;
    }

    public void setStaleTaskThresholdMs(long staleTaskThresholdMs) {
        this.staleTaskThresholdMs = staleTaskThresholdMs;
    }

    public long getMaxTaskRuntimeMs() {
        return maxTaskRuntimeMs;
    }

    public void setMaxTaskRuntimeMs(long maxTaskRuntimeMs) {
        this.maxTaskRuntimeMs = maxTaskRuntimeMs;
    }

    public int getRecoveryBatchSize() {
        return recoveryBatchSize;
    }

    public void setRecoveryBatchSize(int recoveryBatchSize) {
        this.recoveryBatchSize = recoveryBatchSize;
    }
}
