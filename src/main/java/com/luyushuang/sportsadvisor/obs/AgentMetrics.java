package com.luyushuang.sportsadvisor.obs;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class AgentMetrics {

    private final MeterRegistry meterRegistry;
    private final ObservationRegistry observationRegistry;

    // ---- ProfileAgent 指标 ----
    private final Counter profileExtractionSkipped;
    private final Counter profileExtractionFailed;
    private final Counter profileValidationFailed;
    private final Timer profileExtractionTimer;

    // ---- TrainingAgent 指标 ----
    private final Counter planGenerationFailed;
    private final Counter planRevisionTriggered;
    private final Counter safetyBlocked;
    private final Timer planGenerationTimer;

    public AgentMetrics(MeterRegistry meterRegistry,
                        ObservationRegistry observationRegistry) {
        this.meterRegistry = meterRegistry;
        this.observationRegistry = observationRegistry;

        this.profileExtractionSkipped = Counter.builder("sports.profile.extraction.skipped")
                .description("画像提取被关键词预过滤跳过的次数")
                .register(meterRegistry);

        this.profileExtractionFailed = Counter.builder("sports.profile.extraction.failed")
                .description("画像提取失败次数")
                .register(meterRegistry);

        this.profileValidationFailed = Counter.builder("sports.profile.validation.failed")
                .description("画像数值校验失败次数")
                .register(meterRegistry);

        this.profileExtractionTimer = Timer.builder("sports.profile.extraction.latency")
                .description("画像提取耗时")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(meterRegistry);

        this.planGenerationFailed = Counter.builder("sports.plan.generation.failed")
                .description("训练计划生成失败次数")
                .register(meterRegistry);

        this.planRevisionTriggered = Counter.builder("sports.plan.revision.triggered")
                .description("安全审核触发计划修正的次数")
                .register(meterRegistry);

        this.safetyBlocked = Counter.builder("sports.plan.safety.blocked")
                .description("安全审核阻断计划次数")
                .register(meterRegistry);

        this.planGenerationTimer = Timer.builder("sports.plan.generation.latency")
                .description("训练计划生成耗时")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(meterRegistry);
    }

    // ---- 通用观测包装 ----

    /** 在 Observation 中执行业务逻辑，自动记录耗时和异常 */
    public <T> T observe(String observationName, String agent, Runnable... ignored) {
        return Observation.createNotStarted(observationName, observationRegistry)
                .lowCardinalityKeyValue("sports.agent", agent)
                .observe(() -> null);
    }

    public <T> T observe(String observationName, String agent, java.util.function.Supplier<T> action) {
        return Observation.createNotStarted(observationName, observationRegistry)
                .lowCardinalityKeyValue("sports.agent", agent)
                .observe(action);
    }

    // ---- ProfileAgent 埋点 ----

    public void recordProfileExtraction(long durationMs, int extractedFieldCount) {
        profileExtractionTimer.record(durationMs, TimeUnit.MILLISECONDS);
        meterRegistry.counter("sports.profile.extraction.fields",
                        "count", String.valueOf(extractedFieldCount))
                .increment();
    }

    public void recordProfileExtractionSkipped() {
        profileExtractionSkipped.increment();
    }

    public void recordProfileExtractionFailed() {
        profileExtractionFailed.increment();
    }

    public void recordProfileValidationFailed() {
        profileValidationFailed.increment();
    }

    // ---- TrainingAgent 埋点 ----

    public void recordPlanGeneration(long durationMs, int weekCount) {
        planGenerationTimer.record(durationMs, TimeUnit.MILLISECONDS);
        meterRegistry.counter("sports.plan.weeks", "weeks", String.valueOf(weekCount))
                .increment();
    }

    public void recordPlanGenerationFailed() {
        planGenerationFailed.increment();
    }

    public void recordPlanRevisionTriggered() {
        planRevisionTriggered.increment();
    }

    public void recordSafetyBlocked() {
        safetyBlocked.increment();
    }
}