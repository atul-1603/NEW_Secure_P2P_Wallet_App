package com.wallet.app.config;

import java.util.concurrent.Executor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class AsyncConfig {

    @Bean(name = "withdrawalProcessingExecutor")
    public Executor withdrawalProcessingExecutor(
        @Value("${withdrawal.processing.core-pool-size:2}") int corePoolSize,
        @Value("${withdrawal.processing.max-pool-size:4}") int maxPoolSize,
        @Value("${withdrawal.processing.queue-capacity:100}") int queueCapacity
    ) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("withdrawal-processor-");
        executor.initialize();
        return executor;
    }
}
