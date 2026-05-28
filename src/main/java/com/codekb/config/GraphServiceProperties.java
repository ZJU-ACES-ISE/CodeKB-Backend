package com.codekb.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "codekb.graph")
public class GraphServiceProperties {

    private String baseUrl = "http://localhost:8000";
    private long pollIntervalMs = 10000L;
    private int maxPollTimes = 200;
    private int requestTimeoutMs = 10000;
    private long workerLeaseMs = 10 * 60 * 1000L;
    private long recoveryIntervalMs = 1500L;
    private long staleTaskThresholdMs = 10 * 60 * 1000L;
    private long maxTaskRuntimeMs = 5 * 60 * 1000L;
    private int recoveryBatchSize = 8;
    private int remoteMaxConcurrentJobs = 3;
    private int remoteSlowConcurrentJobs = 0;
    private long pendingRetryDelayMs = 3000L;
    private long remoteErrorBackoffMs = 30000L;
    private long submissionCooldownMs = 30000L;
    private long slowQueueThresholdMs = 60000L;
    private long slowPollIntervalMs = 120000L;

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

    public int getRemoteMaxConcurrentJobs() {
        return remoteMaxConcurrentJobs;
    }

    public void setRemoteMaxConcurrentJobs(int remoteMaxConcurrentJobs) {
        this.remoteMaxConcurrentJobs = remoteMaxConcurrentJobs;
    }

    public int getRemoteSlowConcurrentJobs() {
        return remoteSlowConcurrentJobs;
    }

    public void setRemoteSlowConcurrentJobs(int remoteSlowConcurrentJobs) {
        this.remoteSlowConcurrentJobs = remoteSlowConcurrentJobs;
    }

    public long getPendingRetryDelayMs() {
        return pendingRetryDelayMs;
    }

    public void setPendingRetryDelayMs(long pendingRetryDelayMs) {
        this.pendingRetryDelayMs = pendingRetryDelayMs;
    }

    public long getRemoteErrorBackoffMs() {
        return remoteErrorBackoffMs;
    }

    public void setRemoteErrorBackoffMs(long remoteErrorBackoffMs) {
        this.remoteErrorBackoffMs = remoteErrorBackoffMs;
    }

    public long getSubmissionCooldownMs() {
        return submissionCooldownMs;
    }

    public void setSubmissionCooldownMs(long submissionCooldownMs) {
        this.submissionCooldownMs = submissionCooldownMs;
    }

    public long getSlowQueueThresholdMs() {
        return slowQueueThresholdMs;
    }

    public void setSlowQueueThresholdMs(long slowQueueThresholdMs) {
        this.slowQueueThresholdMs = slowQueueThresholdMs;
    }

    public long getSlowPollIntervalMs() {
        return slowPollIntervalMs;
    }

    public void setSlowPollIntervalMs(long slowPollIntervalMs) {
        this.slowPollIntervalMs = slowPollIntervalMs;
    }
}
