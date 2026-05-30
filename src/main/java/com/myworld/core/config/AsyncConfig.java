package com.myworld.core.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.Arrays;
import java.util.concurrent.Executor;

/**
 * FIX: Async thread pool configuration.
 *
 * Problem: @EnableAsync was set in EarnX3Application but no TaskExecutor bean
 * existed. Spring defaulted to SimpleAsyncTaskExecutor which:
 *   - Creates a NEW THREAD for every single @Async call — no pooling
 *   - Has NO upper bound — 1000 concurrent payout events = 1000 threads
 *   - Each thread consumes ~256KB–1MB stack = OOM under burst load
 *
 * Fix: ThreadPoolTaskExecutor with explicit bounds:
 *   - corePoolSize(4)     → 4 threads always warm and ready
 *   - maxPoolSize(20)      → burst capacity up to 20 threads
 *   - queueCapacity(100)  → 100 tasks queued before rejection
 *   - keepAlive(60s)       → idle threads above core shrink after 60s
 *   - CallerRunsPolicy    → if queue is full, caller thread runs the task
 *                           instead of throwing RejectedExecutionException
 *
 * This pool is used by:
 *   - NotificationEventListener (@Async @TransactionalEventListener)
 *   - Any future @Async methods
 *
 * Sizing guidance (adjust per your server):
 *   For I/O-bound async tasks (email, DB notifications):
 *     corePoolSize  = available CPU cores × 2   (e.g., 2-core → 4)
 *     maxPoolSize   = corePoolSize × 5           (e.g., 2-core → 20)
 *   For CPU-bound tasks reduce multiplier to × 1.
 */
@Slf4j
@Configuration
public class AsyncConfig implements AsyncConfigurer {

    @Bean(name = "taskExecutor")
    @Override
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(20);
        executor.setQueueCapacity(100);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("earnx-async-");
        // CallerRunsPolicy: if pool is full, run in the calling thread
        // instead of dropping the task (notification is better late than lost)
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        log.info("[ASYNC] ThreadPoolTaskExecutor ready: core=4 max=20 queue=100");
        return executor;
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        // Log uncaught exceptions from @Async methods — they are silently swallowed by default
        return (ex, method, params) ->
            log.error("[ASYNC] Uncaught exception in @Async method: {}.{}({}) — {}",
                method.getDeclaringClass().getSimpleName(),
                method.getName(),
                Arrays.toString(params),
                ex.getMessage(), ex);
    }
}
