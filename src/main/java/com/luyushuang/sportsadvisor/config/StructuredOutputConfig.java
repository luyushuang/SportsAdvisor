package com.luyushuang.sportsadvisor.config;

import com.luyushuang.sportsadvisor.entity.HalfMarathonPlan;
import com.luyushuang.sportsadvisor.entity.SafetyReviewResult;
import com.luyushuang.sportsadvisor.util.ResilientConverter;
import org.springframework.ai.chat.client.advisor.StructuredOutputValidationAdvisor;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.converter.MarkdownCodeBlockCleaner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

@Configuration
public class StructuredOutputConfig {
    JsonMapper jsonMapper = JsonMapper.builder()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .build();

    /**
     * 训练计划转换器：自动剥离 Markdown 代码块
     */
    @Bean("planConverter")
    public ResilientConverter<HalfMarathonPlan> planConverter() {
        return new ResilientConverter<>(
                HalfMarathonPlan.class
        );
    }

    /**
     * 安全审核结果转换器：自动剥离 Markdown 代码块
     */
    @Bean("safetyReviewConverter")
    public ResilientConverter<SafetyReviewResult> safetyReviewConverter() {
        return new ResilientConverter<>(
                SafetyReviewResult.class
        );
    }

    @Bean("planValidationAdvisor")
    public StructuredOutputValidationAdvisor planValidationAdvisor() {
        return StructuredOutputValidationAdvisor.builder()
                .outputType(HalfMarathonPlan.class) // 显式指定目标类型
                .maxRepeatAttempts(3)               // 可选：自定义重试次数
                .build();
    }

    @Bean("safetyValidationAdvisor")
    public StructuredOutputValidationAdvisor safetyValidationAdvisor() {
        return StructuredOutputValidationAdvisor.builder()
                .outputType(SafetyReviewResult.class)
                .maxRepeatAttempts(3)
                .build();
    }
}