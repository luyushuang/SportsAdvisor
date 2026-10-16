package com.luyushuang.sportsadvisor.service;

import com.luyushuang.sportsadvisor.agent.ProfileAgent;
import com.luyushuang.sportsadvisor.tool.WeatherTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.stereotype.Service;

@Service
public class RunningAdvisorService {

    private static final String SYSTEM_PROMPT = """
            你是一个专业的运动健康助手，帮助用户判断某个中国城市某一天是否适合户外跑步。
            
            ## 工具使用规则
            1. 当用户询问某城市某天的天气或是否适合跑步时，必须调用 getWeatherAndAqi 工具获取真实数据。
            2. 如果用户只说了"明天""后天"等相对日期，请根据今天的日期（%s）推算为 yyyy-MM-dd 格式后再调用工具。
            3. 如果工具返回以"错误："开头的内容，请如实告知用户无法获取数据，不要编造任何天气信息。
            
            ## 跑步适宜性判断标准（基于中国 AQI 分级）
            请结合工具返回的温度、降水、天气状况和 AQI，按以下标准综合判断：
            
            **空气质量（AQI，US AQI 标准，接近中国标准）：**
            - 0-50（优）：各类人群均可正常户外跑步
            - 51-100（良）：适合跑步，敏感人群注意
            - 101-150（轻度污染）：建议减少高强度跑步，敏感人群改为室内
            - 151-200（中度污染）：不建议户外跑步，建议室内运动
            - 201-300（重度污染）：禁止户外跑步
            - >300（严重污染）：禁止一切户外活动
            
            **温度：**
            - 5°C ~ 28°C：适宜
            - 0°C ~ 5°C 或 28°C ~ 32°C：偏冷/偏热，需注意保暖或补水
            - 低于 -5°C 或高于 35°C：不建议户外跑步
            
            **降水与天气：**
            - 降水量 > 5mm 或有雷暴：不建议户外跑步
            - 小雨或毛毛雨：可酌情，但需提醒路面湿滑
            
            ## 回答格式
            先用一句话给出结论（适合/不适合/谨慎推荐），再用 2-3 句话说明依据（引用具体数据），
            最后给出一条针对性的建议（如最佳时段、装备提醒、替代方案）。
            不要罗列原始数据表格，要用自然语言表达。
            """;

    private final ChatClient chatClient;
    private final ProfileAgent profileAgent;

    public RunningAdvisorService(ChatClient.Builder builder,
                                 WeatherTools weatherTools, ChatMemory chatMemory, ProfileAgent profileAgent) {
//        ChatMemory chatMemory = new InMemoryChatMemory();
        this.profileAgent = profileAgent;
        this.chatClient = builder
                .defaultSystem(String.format(SYSTEM_PROMPT, java.time.LocalDate.now()))
                .defaultTools(weatherTools)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();
    }

    /**
     * 单轮对话（无会话上下文）
     */
    public String ask(String question) {
        return chatClient.prompt()
                .user(question)
                .call()
                .content();
    }

    /**
     * 多轮对话（按 conversationId 隔离会话记忆）
     */
    public String ask(String userId, String conversationId, String question) {
        String answer = chatClient.prompt()
                .user(question)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .call()
                .content();

        // 异步提取，不阻塞响应
        profileAgent.extractAndUpdateAsync(userId, question);

        return answer;
    }
}
