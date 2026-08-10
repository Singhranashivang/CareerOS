package com.careeros.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * Kept small on purpose: Ollama is one local instance, so running more
     * onboarding jobs concurrently than this just queues at Ollama anyway
     * while holding extra app threads for no benefit.
     */
    @Bean
    public Executor onboardingExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("onboarding-");
        executor.initialize();
        return executor;
    }
}
