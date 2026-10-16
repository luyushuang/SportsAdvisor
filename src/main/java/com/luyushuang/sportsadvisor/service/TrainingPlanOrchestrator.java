package com.luyushuang.sportsadvisor.service;

import com.luyushuang.sportsadvisor.agent.ProfileAgent;
import com.luyushuang.sportsadvisor.agent.SafetyAgent;
import com.luyushuang.sportsadvisor.agent.TrainingAgent;
import com.luyushuang.sportsadvisor.entity.HalfMarathonPlan;
import com.luyushuang.sportsadvisor.entity.SafetyReviewResult;
import com.luyushuang.sportsadvisor.entity.UserProfile;
import com.luyushuang.sportsadvisor.repository.UserProfileRepository;
import com.luyushuang.sportsadvisor.tool.WeatherTools;
import org.springframework.stereotype.Service;

@Service
public class TrainingPlanOrchestrator {

    private final ProfileAgent profileAgent;
    private final TrainingAgent trainingAgent;
    private final SafetyAgent safetyAgent;
    private final UserProfileRepository profileRepository;
    private final WeatherTools weatherTools;
    private static final int MAX_REVISION_ROUNDS = 2;

    public TrainingPlanOrchestrator(ProfileAgent profileAgent,
                                    TrainingAgent trainingAgent,
                                    SafetyAgent safetyAgent,
                                    UserProfileRepository profileRepository,
                                    WeatherTools weatherTools) {
        this.profileAgent = profileAgent;
        this.trainingAgent = trainingAgent;
        this.safetyAgent = safetyAgent;
        this.profileRepository = profileRepository;
        this.weatherTools = weatherTools;
    }

    /**
     * 编排完整的训练计划生成流程
     */
    public TrainingPlanResponse createPlan(String userId, String userRequest) {
        // Step 1: 画像检查
        // 触发画像自动提取（从本次请求中提取）
        UserProfile userProfile = profileAgent.extractAndUpdate(userId, userRequest);

        // 如果仍然没有关键信息，引导用户补充
        if (userProfile == null || !userProfile.isSufficientForPlan()) {
            return new TrainingPlanResponse(
                    "need_profile",
                    "为了制定个性化计划，我需要知道您的跑龄、周跑量和半马目标。比如：\"我跑龄2年，周跑量30公里，想12周内半马破2\"。",
                    null, null
            );
        }

        // Step 2: 生成计划
        HalfMarathonPlan plan = trainingAgent.generatePlan(userId, userRequest);

        // Step 3: 安全审核 + 自动修正循环
        for (int round = 0; round < MAX_REVISION_ROUNDS; round++) {
            SafetyReviewResult review = safetyAgent.review(plan, userProfile);

            if (review.approved()) {
                return new TrainingPlanResponse("approved",
                        "计划已生成并通过安全审核。", plan, review.warnings());
            }

            if (review.blockers().isEmpty()) {
                // 只有警告，没有阻断，直接返回并附带提醒
                return new TrainingPlanResponse("approved_with_warnings",
                        "计划已生成。请注意以下提醒：", plan, review.warnings());
            }

            // 有阻断性问题，让 Training Agent 根据审核意见修正
            plan = trainingAgent.revisePlan(userId, plan, review.blockers(), userProfile);
        }

        // 超过修正轮数仍未通过，返回阻断状态
        return new TrainingPlanResponse("blocked",
                "计划经过多轮调整仍未通过安全审核，请人工介入检查。", plan, null);
    }

    // 响应 DTO
    public record TrainingPlanResponse(
            String status,              // approved / blocked / need_profile
            String message,
            HalfMarathonPlan plan,
            java.util.List<String> warnings
    ) {
    }
}