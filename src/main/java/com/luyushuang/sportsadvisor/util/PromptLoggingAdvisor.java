package com.luyushuang.sportsadvisor.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.prompt.Prompt;

@Slf4j
public class PromptLoggingAdvisor implements CallAdvisor {

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        // 1. 从 ChatClientRequest 中获取最终要发送的 Prompt
        Prompt finalPrompt = request.prompt();

        // 2. 打印 Prompt 中的所有消息（System, User, Assistant, Tool等）
        log.info("====== FINAL PROMPT TO LLM ======");
        finalPrompt.getInstructions().forEach(message -> {
            log.info("【{}】: {}", message.getMessageType(), message.getText());
        });
        log.info("=================================");

        // 3. 继续执行 Advisor 链，将请求传递给下一个 Advisor 或模型
        return chain.nextCall(request);
    }

    @Override
    public String getName() {
        return "PromptLoggingAdvisor";
    }

    @Override
    public int getOrder() {
        // 返回值越小，执行越晚，越靠近模型调用。
        // 使用 LOWEST_PRECEDENCE 可以确保它是最后一个执行的，捕获到的 Prompt 最完整。
        return 100;
    }
}