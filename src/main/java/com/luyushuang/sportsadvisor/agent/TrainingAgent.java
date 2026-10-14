package com.luyushuang.sportsadvisor.agent;

import com.luyushuang.sportsadvisor.entity.HalfMarathonPlan;
import com.luyushuang.sportsadvisor.entity.UserProfile;
import com.luyushuang.sportsadvisor.tool.WeatherTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TrainingAgent {
    private final ChatClient trainingChatClient;
    private final WeatherTools weatherTools;
    private final ProfileAgent profileAgent;

    public TrainingAgent(ChatClient.Builder builder,
                         WeatherTools weatherTools,
                         ProfileAgent profileAgent,
                         VectorStore vectorStore,
                         ChatMemory chatMemory) {
        this.weatherTools = weatherTools;
        this.profileAgent = profileAgent;

        this.trainingChatClient = builder
                .defaultSystem("""
                        你是一个专业的半马训练计划制定专家，拥有运动科学和跑步训练的专业知识。
                        
                        ## 你的任务
                        根据用户画像、环境信息和检索到的运动科学知识，制定个性化的半马训练计划。
                        
                        ## 制定原则
                        1. 训练计划必须基于用户当前周跑量和跑龄，遵循"10%原则"（每周跑量增量不超过上周10%）
                        2. 必须包含以下训练类型：轻松跑(Easy Run)、长距离跑(Long Run)、间歇跑(Interval)、恢复跑(Recovery Run)、交叉训练(Cross Training)、休息日(Rest Day)
                        3. 必须引用知识库中检索到的具体训练方法和配速计算方式
                        4. 如果用户有伤病历史，必须在计划中体现预防措施
                        5. 结合天气数据，在极端天气日建议室内训练
                        
                        ## 输出格式
                        请严格按照要求的 JSON 格式输出训练计划。
                        """)
                .defaultTools(this.weatherTools)
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(chatMemory).build(),
                        QuestionAnswerAdvisor.builder(vectorStore)
                                .searchRequest(SearchRequest.builder().topK(8).similarityThreshold(0.65).build())
                                .build()
                )
                .build();
    }

    /**
     * 生成半马训练计划
     */
    public HalfMarathonPlan generatePlan(String userId, String userRequest) {
        // 1. 加载用户画像
        String profileContext = profileAgent.buildProfileContext(userId);

        // 2. 构建包含画像的完整请求
        String fullPrompt = String.format("""
                用户画像信息：
                %s
                
                用户请求：%s
                
                请基于以上用户画像和检索到的训练知识，生成一份完整的半马训练计划。
                """, profileContext, userRequest);

        // todo:
        SearchRequest searchRequest = SearchRequest.builder()
                .query(userRequest)
                .topK(8)
                .similarityThreshold(0.65)
                .filterExpression("category == 'training' || category == 'pace'")
                .build();

        // 3. 调用 LLM，返回结构化训练计划
        return trainingChatClient.prompt()
                .user(fullPrompt)
                .call()
                .entity(HalfMarathonPlan.class);
    }

    // 在 TrainingAgent 中新增
    public HalfMarathonPlan revisePlan(String userId,
                                       HalfMarathonPlan originalPlan,
                                       List<String> blockers,
                                       UserProfile profile) {
        String revisePrompt = String.format("""
                        以下训练计划存在安全问题，请修正：
                        
                        ## 安全问题
                        %s
                        
                        ## 原始计划
                        %s
                        
                        ## 用户画像
                        周跑量：%.1fkm，跑龄：%s
                        
                        请修正上述问题，保持计划整体框架不变，只调整有问题的部分。
                        """,
                String.join("\n", blockers),
                new BeanOutputConverter<>(HalfMarathonPlan.class).convert(originalPlan),
                profile.weeklyMileageKm(), profile.runningExperience());

        return trainingChatClient.prompt()
                .user(revisePrompt)
                .call()
                .entity(HalfMarathonPlan.class);
    }
}
