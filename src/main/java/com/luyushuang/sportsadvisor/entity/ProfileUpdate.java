package com.luyushuang.sportsadvisor.entity;

import java.util.List;

public record ProfileUpdate(
        Integer age,
        Double weightKg,
        Integer heightCm,
        String runningExperience,
        Double weeklyMileageKm,
        String bestHalfMarathonTime,
        String trainingGoal,
        List<String> injuryHistory,
        String preferredRunTime,
        String preferredTerrain
) {}