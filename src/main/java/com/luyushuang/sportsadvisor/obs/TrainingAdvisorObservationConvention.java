package com.luyushuang.sportsadvisor.obs;

import io.micrometer.common.KeyValues;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.advisor.observation.AdvisorObservationContext;
import org.springframework.ai.chat.client.advisor.observation.AdvisorObservationConvention;

import java.util.Map;

public class TrainingAdvisorObservationConvention implements AdvisorObservationConvention {

    private static final String DEFAULT_NAME = "sports.advisor.training";

    @Override
    public String getName() {
        return DEFAULT_NAME;
    }

    @Override
    public String getContextualName(AdvisorObservationContext context) {
        return DEFAULT_NAME + "." + context.getAdvisorName();
    }

    @Override
    public KeyValues getLowCardinalityKeyValues(AdvisorObservationContext context) {
        return KeyValues.of(
                "sports.agent", "profile",
                "sports.agent.operation", context.getAdvisorName()
        );
    }

    @Override
    public KeyValues getHighCardinalityKeyValues(AdvisorObservationContext context) {
        ChatClientRequest request = context.getChatClientRequest();
        // 从 ChatClientRequest 中获取上下文 Map
        Map<String, Object> contextMap = request.context();
        Object userId = contextMap.get("userId");
        return KeyValues.of(
                "sports.user.id", String.valueOf(userId != null ? userId : "unknown")
        );
    }

    @Override
    public boolean supportsContext(io.micrometer.observation.Observation.Context context) {
        return context instanceof AdvisorObservationContext;
    }
}