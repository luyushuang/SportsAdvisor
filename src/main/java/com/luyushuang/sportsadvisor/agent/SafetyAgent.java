package com.luyushuang.sportsadvisor.agent;

import com.luyushuang.sportsadvisor.entity.HalfMarathonPlan;
import com.luyushuang.sportsadvisor.entity.SafetyReviewResult;
import com.luyushuang.sportsadvisor.entity.UserProfile;
import com.luyushuang.sportsadvisor.util.PromptLoggingAdvisor;
import com.luyushuang.sportsadvisor.util.ResilientConverter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.StructuredOutputValidationAdvisor;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class SafetyAgent {

    private final ChatClient safetyChatClient;

    private final SafetyRuleEngine safetyRuleEngine;

    private final ResilientConverter<SafetyReviewResult> reviewConverter;
    private final StructuredOutputValidationAdvisor safetyValidationAdvisor;

    public SafetyAgent(ChatClient.Builder builder, SafetyRuleEngine safetyRuleEngine,
                       ResilientConverter<SafetyReviewResult> reviewConverter,
                       StructuredOutputValidationAdvisor safetyValidationAdvisor) {
        this.safetyRuleEngine = safetyRuleEngine;
        this.reviewConverter = reviewConverter;
        this.safetyValidationAdvisor = safetyValidationAdvisor;
        this.safetyChatClient = builder
                .defaultSystem("""
                        你是一个运动安全审核专家。你的职责是审核半马训练计划是否安全合理。
                        
                        ## 审核清单
                        1. **跑量递增**：每周跑量增幅是否超过10%？
                        2. **休息日**：每周是否至少有1-2天完全休息？
                        3. **强度分布**：高强度训练（间歇跑/节奏跑）是否超过每周总跑量的20%？
                        4. **长距离跑**：长距离跑的递增是否合理（每周增幅不超过10%）？
                        5. **伤病冲突**：如果用户有伤病历史，计划中是否包含相关预防措施和替代训练？
                        6. **极端天气**：是否在极端天气日仍安排了户外训练？
                        7. **医疗边界**：是否涉及具体的医疗建议（如"你的膝盖疼应该吃XX药"）？
                        
                        ## 审核结论
                        - 如果发现严重问题（如跑量暴增、伤病冲突、医疗建议），标记为 blocker，必须修改
                        - 如果发现轻微问题（如休息日偏少），标记为 warning，建议但不阻断
                        - 如果没有问题，返回 approved=true
                        """)
                .defaultAdvisors(new PromptLoggingAdvisor(), safetyValidationAdvisor)
                .build();
    }

    /**
     * 审核训练计划
     */
    public SafetyReviewResult review(HalfMarathonPlan plan, UserProfile profile) {
        // 先跑规则引擎
        List<String> ruleBlockers = safetyRuleEngine.checkRules(plan, profile);

        if (ruleBlockers.isEmpty()) {
            log.debug("--规则引擎通过");
        } else {
            log.debug("--规则引擎运行结果：");

            for (String str : ruleBlockers) {
                log.debug("----blocker: {}", str);
            }
        }

        // 再交给 LLM 做综合审核
        String reviewPrompt = String.format("""
                        请审核以下半马训练计划。
                        
                        ## 规则引擎已发现的问题
                        %s
                        
                        ## 用户画像
                        年龄：%d，跑龄：%s，周跑量：%.1fkm，伤病历史：%s
                        
                        ## 训练计划
                        %s
                        
                        请综合规则引擎结果和你的专业判断，给出最终审核结论。
                        """,
                ruleBlockers.isEmpty() ? "无" : String.join("\n", ruleBlockers),
                profile.age(), profile.runningExperience(), profile.weeklyMileageKm(),
                profile.injuryHistory().isEmpty() ? "无" : String.join("、", profile.injuryHistory()),
                plan);
        log.debug("reviewPrompt: {}", reviewPrompt);

        return safetyChatClient.prompt().user(reviewPrompt).call()
                .entity(reviewConverter);
    }
}
