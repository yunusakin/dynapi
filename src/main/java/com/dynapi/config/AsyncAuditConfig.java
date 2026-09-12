package com.dynapi.config;

import java.util.concurrent.Executor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.task.DelegatingSecurityContextAsyncTaskExecutor;

@Configuration
@EnableAsync
public class AsyncAuditConfig {
    public static final String AUDIT_EXECUTOR = "auditTaskExecutor";

    @Bean(AUDIT_EXECUTOR)
    public Executor auditTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("audit-");
        executor.initialize();
        // Wrap so the calling thread's SecurityContext (needed by CurrentActorResolver) is
        // propagated to the async thread; SecurityContextHolder is ThreadLocal-based by default
        // and would otherwise be empty on every audit write, resolving every actor to "system".
        return new DelegatingSecurityContextAsyncTaskExecutor(executor);
    }
}
