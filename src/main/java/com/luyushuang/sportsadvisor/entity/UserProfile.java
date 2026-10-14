package com.luyushuang.sportsadvisor.entity;

import java.util.List;

public record UserProfile(
        String userId,
        Integer age,
        Double weightKg,
        Integer heightCm,
        String runningExperience,   // 跑龄：新手/1年内/1-3年/3年以上
        Double weeklyMileageKm,      // 周跑量
        String bestHalfMarathonTime, // 半马最好成绩
        String trainingGoal,         // 目标：完赛/破2/破150等
        List<String> injuryHistory,  // 伤病历史
        String preferredRunTime,     // 偏好时段：早晨/傍晚/夜间
        String preferredTerrain     // 偏好路面：公路/跑道/越野
) {
}
