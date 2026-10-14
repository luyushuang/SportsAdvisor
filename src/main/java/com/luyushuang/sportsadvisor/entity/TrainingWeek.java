package com.luyushuang.sportsadvisor.entity;

import java.util.List;

public record TrainingWeek(
        int weekNumber,            // 第几周
        String focus,              // 本周训练重点
        List<TrainingDay> days     // 每日训练安排
) {}
