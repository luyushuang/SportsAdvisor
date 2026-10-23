package com.luyushuang.sportsadvisor.agent;

import com.luyushuang.sportsadvisor.entity.HalfMarathonPlan;
import com.luyushuang.sportsadvisor.entity.UserProfile;
import com.luyushuang.sportsadvisor.obs.AgentMetrics;
import com.luyushuang.sportsadvisor.obs.TrainingAdvisorObservationConvention;
import com.luyushuang.sportsadvisor.tool.WeatherTools;
import com.luyushuang.sportsadvisor.util.PromptLoggingAdvisor;
import com.luyushuang.sportsadvisor.util.ResilientConverter;
import com.luyushuang.sportsadvisor.util.TrainingPrompts;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.StructuredOutputValidationAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class TrainingAgent {

    private final ChatClient trainingChatClient;
    private final ChatClient reviseChatClient;
    private final WeatherTools weatherTools;
    private final ProfileAgent profileAgent;
    private final ResilientConverter<HalfMarathonPlan> planConverter;
    private final StructuredOutputValidationAdvisor planValidationAdvisor;
    private final AgentMetrics metrics;
    private final ObservationRegistry observationRegistry;
    private final ChatModel chatModel;

    public TrainingAgent(WeatherTools weatherTools,
                         ProfileAgent profileAgent,
                         VectorStore vectorStore,
                         ChatMemory chatMemory,
                         ResilientConverter<HalfMarathonPlan> planConverter,
                         StructuredOutputValidationAdvisor planValidationAdvisor,
                         AgentMetrics metrics,
                         ObservationRegistry observationRegistry, ChatModel chatModel) {

        this.weatherTools = weatherTools;
        this.profileAgent = profileAgent;
        this.planConverter = planConverter;
        this.planValidationAdvisor = planValidationAdvisor;
        this.metrics = metrics;
        this.observationRegistry = observationRegistry;

        // ---- 构建 RAG Advisor（替代已移除的 QuestionAnswerAdvisor）----
        RetrievalAugmentationAdvisor ragAdvisor = RetrievalAugmentationAdvisor.builder()
                .documentRetriever(
                        VectorStoreDocumentRetriever.builder()
                                .vectorStore(vectorStore)
                                .similarityThreshold(0.65)
                                .topK(8)
                                .build()
                ).build();

        // ---- 构建 ChatMemory Advisor ----
        MessageChatMemoryAdvisor memoryAdvisor = MessageChatMemoryAdvisor
                .builder(chatMemory)
                .build();

        // ---- 构建 TrainingAgent 专用的 ChatClient ----
        this.chatModel = chatModel;
        this.trainingChatClient = ChatClient.builder(chatModel, observationRegistry,
                        null, new TrainingAdvisorObservationConvention())
                .defaultSystem(TrainingPrompts.GENERATE_SYSTEM)
                .defaultTools(weatherTools)
                .defaultAdvisors(new PromptLoggingAdvisor(), memoryAdvisor, ragAdvisor, planValidationAdvisor)
                .build();
        this.reviseChatClient = ChatClient.builder(chatModel, observationRegistry,
                        null, new TrainingAdvisorObservationConvention())
                .defaultSystem(TrainingPrompts.REVISE_SYSTEM)
                .defaultTools(weatherTools)
                .defaultAdvisors(new PromptLoggingAdvisor(), ragAdvisor, planValidationAdvisor)
                .build();

    }

    /**
     * 生成半马训练计划
     */
    public HalfMarathonPlan generatePlan(String userId, String userRequest, int effectiveWeeks) {
        log.info("开始生成训练计划，userId={}, targetWeeks={}, requestLength={}",
                userId, effectiveWeeks, userRequest.length());

        String profileContext = profileAgent.buildProfileContext(userId);

        // 关键：周数通过 User Prompt 动态注入，System Prompt 不含具体数字
        String fullPrompt = String.format("""
                    用户画像信息：
                    %s

                    用户请求：%s

                    ## 本次计划的周数要求
                    计划必须恰好包含 %d 周，每周恰好包含 7 天。
                    禁止增加或减少周数，禁止省略任何一周。

                    请基于以上用户画像、用户请求和检索到的训练知识，生成一份恰好 %d 周的半马训练计划。
                    """, profileContext, userRequest, effectiveWeeks, effectiveWeeks);
//        训练计划必须满足如下要求：1)训练强度合理，保证每周至少休息1天，休息日的workoutType必须为'休息'。2)本周总跑量增幅不超过上一周总跑量的10%%。
        log.debug("generatePrompt: {}", fullPrompt);

        return Observation.createNotStarted("sports.plan.generate", observationRegistry)
                .lowCardinalityKeyValue("sports.agent", "training")
                .highCardinalityKeyValue("sports.user.id", userId)
                .observe(() -> {
                    long start = System.currentTimeMillis();
                    try {
                        HalfMarathonPlan plan = trainingChatClient.prompt()
                                .user(fullPrompt)
                                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, userId))
                                .call()
                                .entity(planConverter);

                        // 后置校验：周数必须与请求一致
                        validateWeekCount(plan, effectiveWeeks, "生成");

                        long duration = System.currentTimeMillis() - start;
                        metrics.recordPlanGeneration(duration, plan.weeks().size());

                        log.info("训练计划生成完成，userId={}, planName={}, weeks={}, durationMs={}",
                                userId, plan.planName(), plan.weeks().size(), duration);

                        return plan;

                    } catch (Exception e) {
                        metrics.recordPlanGenerationFailed();
                        long duration = System.currentTimeMillis() - start;
                        log.error("训练计划生成失败，userId={}, targetWeeks={}, durationMs={}",
                                userId, effectiveWeeks, duration, e);
                        throw e;
                    }
                });
    }

    /**
     * 根据安全审核意见修正训练计划
     */
    public HalfMarathonPlan revisePlan(String userId,
                                       HalfMarathonPlan originalPlan,
                                       List<String> blockers,
                                       UserProfile profile) {
        int lockedWeeks = originalPlan.weeks().size();

        log.info("开始修正训练计划，userId={}, lockedWeeks={}, blockerCount={}",
                userId, lockedWeeks, blockers.size());

        long start = System.currentTimeMillis();
        try {
            // 关键：周数从 originalPlan 读取，并在 User Prompt 中显式锁定
            String revisePrompt = String.format("""
                    以下训练计划存在安全问题，请修正：

                    ## 安全问题（blockers）
                    %s

                    ## 原始计划
                    - 计划名称：%s
                    - 周数：%d 周（修正后必须保持 %d 周，禁止增加或减少）
                    - 各周训练摘要：%s

                    ## 用户画像
                    周跑量：%skm，跑龄：%s

                    ## 修正要求
                    1. 只修正上述 blockers 指出的问题，保持计划整体框架不变
                    2. 修正后的计划必须仍然是 %d 周，每周恰好 7 天
                    3. 未涉及修正的周次必须原样保留
                    """,
                    String.join("\n", blockers),
                    originalPlan.planName(),
                    lockedWeeks, lockedWeeks,
                    summarizeWeeks(originalPlan),
                    profile.weeklyMileageKm() != null ? profile.weeklyMileageKm() : "未知",
                    profile.runningExperience() != null ? profile.runningExperience() : "未知",
                    lockedWeeks);

            HalfMarathonPlan revised = reviseChatClient.prompt()
                    .user(revisePrompt)
                    .call()
                    .entity(planConverter);

            // 后置校验：周数必须与原始计划一致
            validateWeekCount(revised, lockedWeeks, "修正");

            long duration = System.currentTimeMillis() - start;
            log.info("训练计划修正完成，userId={}, planName={}, weeks={}, durationMs={}",
                    userId, revised.planName(), revised.weeks().size(), duration);

            return revised;

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - start;
            log.error("训练计划修正失败，userId={}, lockedWeeks={}, durationMs={}",
                    userId, lockedWeeks, duration, e);
            throw e;
        }
    }

    /**
     * 周数后置校验：确保模型没有擅自改变周数。
     */
    private void validateWeekCount(HalfMarathonPlan plan, int expectedWeeks, String scene) {
        if (plan == null || plan.weeks() == null) {
            throw new IllegalStateException(scene + "返回的计划为空");
        }
        if (plan.weeks().size() != expectedWeeks) {
            throw new IllegalStateException(String.format(
                    "%s返回的周数与预期不一致：预期 %d 周，实际 %d 周",
                    scene, expectedWeeks, plan.weeks().size()));
        }
    }

    /**
     * 生成各周训练摘要，帮助模型理解原始计划结构，避免"重写"。
     */
    private String summarizeWeeks(HalfMarathonPlan plan) {
        return plan.weeks().stream()
                .map(w -> "第" + w.weekNumber() + "周【" + w.focus() + "】："
                        + w.days().stream()
                        .map(d -> d.dayOfWeek() + "-" + d.workoutType())
                        .collect(Collectors.joining("、", "（", "）")))
                .collect(Collectors.joining("；"));
    }
}
