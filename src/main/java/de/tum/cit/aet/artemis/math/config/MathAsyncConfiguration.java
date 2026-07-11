package de.tum.cit.aet.artemis.math.config;

import java.util.concurrent.Executor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Two isolated thread pools for asynchronous math grading (Phase 2b, fast/slow lanes):
 * <ul>
 * <li>{@code mathFastGradingExecutor} — high concurrency, for the fast preliminary graders (eggregate, cvc5)
 * that return in well under a second.</li>
 * <li>{@code mathSlowGradingExecutor} — a small, bounded, isolated pool for slow formal certifiers
 * (leanregate, coqregate) whose multi-second proofs must never starve the fast lane.</li>
 * </ul>
 * {@code @EnableAsync} is already declared globally in the core {@code AsyncConfiguration}; these beans just
 * provide the named executors selected via {@code @Async("mathFastGradingExecutor" | "mathSlowGradingExecutor")}.
 */
@Configuration
@Conditional(MathEnabled.class)
public class MathAsyncConfiguration {

    /**
     * @return the high-concurrency executor for the fast preliminary graders (eggregate, cvc5)
     */
    @Bean(name = "mathFastGradingExecutor")
    public Executor mathFastGradingExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("math-grade-fast-");
        executor.initialize();
        return executor;
    }

    /**
     * @return the small, isolated, bounded executor for the slow formal certifiers (leanregate, coqregate)
     */
    @Bean(name = "mathSlowGradingExecutor")
    public Executor mathSlowGradingExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(3);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("math-certify-slow-");
        executor.initialize();
        return executor;
    }
}
