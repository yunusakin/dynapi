package com.dynapi.config;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.task.DelegatingSecurityContextAsyncTaskExecutor;

@Slf4j
@Configuration
@EnableAsync
public class AsyncAuditConfig {
    public static final String AUDIT_EXECUTOR = "auditTaskExecutor";

    /**
     * Registered as its own bean (rather than only wrapped below) so Spring calls its
     * {@code destroy()} on context shutdown — {@link DelegatingSecurityContextAsyncTaskExecutor}
     * does not implement DisposableBean/Lifecycle, so wrapping alone would leak this pool's threads
     * across context restarts.
     */
    @Bean
    public ThreadPoolTaskExecutor auditThreadPoolTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("audit-");
        // A full queue must never throw back to the caller: AuditService.record() is documented to
        // never let an audit failure surface as a failed mutation, but a RejectedExecutionException
        // from the default AbortPolicy is thrown at submission time, on the caller's thread, before
        // record()'s own try/catch ever runs. Discard-and-log instead.
        executor.setRejectedExecutionHandler(
                (runnable, executorService) ->
                        log.error(
                                "Audit task queue is full (capacity=500); dropping an audit write."
                                        + " Active={}, queued={}",
                                ((ThreadPoolExecutor) executorService).getActiveCount(),
                                ((ThreadPoolExecutor) executorService).getQueue().size()));
        executor.initialize();
        return executor;
    }

    /**
     * Wraps the pool above so the calling thread's SecurityContext (needed by
     * CurrentActorResolver) is propagated to the async thread; SecurityContextHolder is
     * ThreadLocal-based by default and would otherwise be empty on every audit write, resolving
     * every actor to "system".
     */
    @Bean(AUDIT_EXECUTOR)
    public Executor auditTaskExecutor(ThreadPoolTaskExecutor auditThreadPoolTaskExecutor) {
        return new DelegatingSecurityContextAsyncTaskExecutor(auditThreadPoolTaskExecutor);
    }
}
