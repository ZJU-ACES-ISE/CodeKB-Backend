package com.codekb.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "codekbAsyncExecutor")
    public Executor codekbAsyncExecutor() {
        return buildExecutor(8, 16, 500, "codekb-analysis-");
    }

    @Bean(name = "codekbGraphExecutor")
    public Executor codekbGraphExecutor() {
        return buildExecutor(4, 8, 200, "codekb-graph-");
    }

    private Executor buildExecutor(int corePoolSize,
                                   int maxPoolSize,
                                   int queueCapacity,
                                   String threadNamePrefix) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setThreadNamePrefix(threadNamePrefix);
        executor.initialize();
        return executor;
    }
}

