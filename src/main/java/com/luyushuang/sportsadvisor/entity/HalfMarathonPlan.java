package com.luyushuang.sportsadvisor.entity;

import java.util.List;

public record HalfMarathonPlan(
        String planName,           // 计划名称，如"12周半马破2计划"
        String summary,            // 计划摘要
        List<TrainingWeek> weeks   // 按周划分的训练安排
) {}
