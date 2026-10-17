package com.luyushuang.sportsadvisor.entity;

import java.util.List;

public record SafetyReviewResult(
        boolean approved,          // 是否通过审核
        List<String> warnings,     // 警告信息（不阻断，但需提醒用户）
        List<String> blockers,     // 阻断性问题（必须修改）
        HalfMarathonPlan revisedPlan     // 如果需要修改，返回修改后的计划JSON
) {}
