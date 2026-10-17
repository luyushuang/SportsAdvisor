package com.luyushuang.sportsadvisor.agent;

import com.luyushuang.sportsadvisor.entity.HalfMarathonPlan;
import com.luyushuang.sportsadvisor.entity.UserProfile;
import com.luyushuang.sportsadvisor.obs.AgentMetrics;
import com.luyushuang.sportsadvisor.obs.TrainingAdvisorObservationConvention;
import com.luyushuang.sportsadvisor.tool.WeatherTools;
import com.luyushuang.sportsadvisor.util.ResilientConverter;
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

@Slf4j
@Service
public class TrainingAgent {

    private final ChatClient trainingChatClient;
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
                )
                .build();

        // ---- 构建 ChatMemory Advisor ----
        MessageChatMemoryAdvisor memoryAdvisor = MessageChatMemoryAdvisor
                .builder(chatMemory)
                .build();

        // ---- 构建 TrainingAgent 专用的 ChatClient ----
        this.chatModel = chatModel;
        this.trainingChatClient = ChatClient.builder(chatModel, observationRegistry,
                        null, new TrainingAdvisorObservationConvention())
                .defaultSystem("""
                        你是一个专业的半马训练计划制定专家，拥有运动科学和跑步训练的专业知识。
                        
                        ## 你的任务
                        根据用户画像、环境信息和检索到的运动科学知识，制定个性化的半马训练计划。
                        
                        ## 制定原则
                        1. 训练计划必须基于用户当前周跑量和跑龄，遵循"10%原则"（每周跑量增量不超过上周10%）
                        2. 必须包含以下训练类型：轻松跑(Easy Run)、长距离跑(Long Run)、间歇跑(Interval)、
                           恢复跑(Recovery Run)、交叉训练(Cross Training)、休息日(Rest Day)
                        3. 必须引用知识库中检索到的具体训练方法和配速计算方式
                        4. 如果用户有伤病历史，必须在计划中体现预防措施
                        5. 结合天气数据，在极端天气日建议室内训练
                        
                        ## 输出格式
                        请严格按照要求的 JSON 格式输出训练计划。
                        """)
                .defaultTools(weatherTools)
                .defaultAdvisors(memoryAdvisor, ragAdvisor, planValidationAdvisor)
                .build();
    }

    /**
     * 生成半马训练计划
     */
    public HalfMarathonPlan generatePlan(String userId, String userRequest) {
        String profileContext = profileAgent.buildProfileContext(userId);

        String fullPrompt = String.format("""
                用户画像信息：
                %s
                
                用户请求：%s
                
                请基于以上用户画像和检索到的训练知识，生成一份完整的半马训练计划。
                """, profileContext, userRequest);
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

                        long duration = System.currentTimeMillis() - start;
                        metrics.recordPlanGeneration(duration, plan.weeks().size());
                        return plan;

                    } catch (Exception e) {
                        metrics.recordPlanGenerationFailed();
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
        metrics.recordPlanRevisionTriggered();
        String revisePrompt = String.format("""
                        以下训练计划存在安全问题，请修正：
                        
                        ## 安全问题
                        %s
                        
                        ## 原始计划摘要
                        计划名称：%s，共 %d 周
                        
                        ## 用户画像
                        周跑量：%.1fkm，跑龄：%s
                        
                        请修正上述问题，保持计划整体框架不变，只调整有问题的部分。
                        休息日的workoutType必须为'休息'。按用户要求返回指定的训练周数。
                        """,
                String.join("\n", blockers),
                originalPlan.planName(),
                originalPlan.weeks().size(),
                profile.weeklyMileageKm(),
                profile.runningExperience());

        log.debug("revisePrompt: {}", revisePrompt);

        return trainingChatClient.prompt()
                .user(revisePrompt)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, userId))
                .call()
                .entity(planConverter);
    }
}
