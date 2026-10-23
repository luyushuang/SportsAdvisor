package com.luyushuang.sportsadvisor.service;

import com.luyushuang.sportsadvisor.agent.ProfileAgent;
import com.luyushuang.sportsadvisor.agent.SafetyAgent;
import com.luyushuang.sportsadvisor.agent.TrainingAgent;
import com.luyushuang.sportsadvisor.entity.HalfMarathonPlan;
import com.luyushuang.sportsadvisor.entity.SafetyReviewResult;
import com.luyushuang.sportsadvisor.entity.UserProfile;
import com.luyushuang.sportsadvisor.persistence.UserProfileRepository;
import com.luyushuang.sportsadvisor.tool.WeatherTools;
import com.luyushuang.sportsadvisor.util.JsonUtils;
import com.luyushuang.sportsadvisor.util.WeekUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.luyushuang.sportsadvisor.util.WeekUtil.clampWeeks;
import static com.luyushuang.sportsadvisor.util.WeekUtil.parseRequestedWeeks;

@Slf4j
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
        // 1. 解析 + 校正周数
        Integer requestedWeeks = parseRequestedWeeks(userRequest);
        int effectiveWeeks = clampWeeks(requestedWeeks);

        List<String> warnings = new ArrayList<>();

        // 2. 周数被调整时，加入警告
        if (requestedWeeks != null && requestedWeeks != effectiveWeeks) {
            if (requestedWeeks < MIN_WEEKS) {
                warnings.add(String.format(
                        "您要求 %d 周，已调整为 %d 周（最小可行训练周期为 %d 周，"
                                + "更短的时间无法构成完整的负荷-恢复-适应循环）",
                        requestedWeeks, effectiveWeeks, MIN_WEEKS));
            } else {
                warnings.add(String.format(
                        "您要求 %d 周，已调整为 %d 周（超出系统支持的训练周期上限）",
                        requestedWeeks, effectiveWeeks));
            }
        }

        // 3. 周数偏短但合法时，加入专业建议
        if (effectiveWeeks < 8) {
            warnings.add(String.format(
                    "%d 周的训练计划主要用于赛前调整和状态保持，不足以产生显著的能力提升。"
                            + "如需系统性提升，建议 8-16 周的训练周期。", effectiveWeeks));
        }

        log.debug("开始查询用户profile，没有的话进行信息提取");
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

        log.debug("已完成用户profile提取，profile={}", JsonUtils.toJson(userProfile));
        log.debug("开始生成训练计划 ...");
        // Step 2: 生成计划
        HalfMarathonPlan plan = trainingAgent.generatePlan(userId, userRequest, effectiveWeeks);

        log.debug("已经生成训练计划：{}", JsonUtils.toJson(plan));
        log.debug("开始安全审核 ...");

        // Step 3: 安全审核 + 自动修正循环
        for (int round = 0; round < MAX_REVISION_ROUNDS; round++) {
            log.debug("第{}轮审核：", round + 1);
            SafetyReviewResult review = safetyAgent.review(plan, userProfile);
            log.debug("-- review result: {}", JsonUtils.toJson(review));

            if (review.approved()) {
                warnings.addAll(review.warnings());
                return new TrainingPlanResponse("approved",
                        "计划已生成并通过安全审核。", plan, warnings);
            }

            if (review.blockers().isEmpty()) {
                warnings.addAll(review.warnings());
                // 只有警告，没有阻断，直接返回并附带提醒
                return new TrainingPlanResponse("approved_with_warnings",
                        "计划已生成。请注意以下提醒：", plan, warnings);
            }

            log.debug("有阻断性问题，让 Training Agent 根据审核意见修正");
            // 有阻断性问题，让 Training Agent 根据审核意见修正
            plan = trainingAgent.revisePlan(userId, plan, review.blockers(), userProfile);
            log.debug("第{}次修改完成，revised plan: {}", round + 1, JsonUtils.toJson(plan));
        }

        log.debug("完成计划审核，返回");

        // 超过修正轮数仍未通过，返回阻断状态
        return new TrainingPlanResponse("blocked",
                "计划经过多轮调整仍未通过安全审核，请人工介入检查。", plan, null);
    }

    private static final int MIN_WEEKS = 4;   // 低于 4 周无法构成有效训练周期
    private static final int MAX_WEEKS = 24;  // 超过 24 周难以维持计划执行

    private int extractTargetWeeks(String userRequest) {
        Matcher m = Pattern.compile("(\\d+)\\s*周").matcher(userRequest);
        if (!m.find()) return 12;  // 未指定，默认 12 周
        int weeks = Integer.parseInt(m.group(1));

        if (weeks < MIN_WEEKS) {
            log.warn("用户请求周数过短：{}周，低于最小阈值{}周", weeks, MIN_WEEKS);
            return MIN_WEEKS;  // 提升到最小值，并在响应中说明
        }
        if (weeks > MAX_WEEKS) {
            log.warn("用户请求周数过长：{}周，超过最大阈值{}周", weeks, MAX_WEEKS);
            return MAX_WEEKS;
        }
        return weeks;
    }

    // 响应 DTO
    public record TrainingPlanResponse(
            String status,              // approved / blocked / need_profile
            String message,
            HalfMarathonPlan plan,
            List<String> warnings
    ) {
    }
}