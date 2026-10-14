package com.luyushuang.sportsadvisor.entity;

public record TrainingDay(
        String dayOfWeek,          // 周一~周日
        String workoutType,        // 训练类型：轻松跑/间歇跑/长距离跑/休息/交叉训练
        String description,        // 训练描述（配速、距离、时长）
        String targetZone,         // 目标心率区间
        String notes               // 备注（如"注意补充电解质"）
) {
}