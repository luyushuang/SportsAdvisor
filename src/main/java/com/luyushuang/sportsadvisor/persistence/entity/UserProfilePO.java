package com.luyushuang.sportsadvisor.persistence.entity;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class UserProfilePO {

    private String userId;
    private Integer age;
    private Double weightKg;
    private Integer heightCm;
    private String runningExperience;
    private Double weeklyMileageKm;
    private String bestHalfMarathonTime;
    private String trainingGoal;
    private List<String> injuryHistory;
    private String preferredRunTime;
    private String preferredTerrain;
    private Integer version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // ---- getters / setters（省略，用 Lombok 或 IDE 生成）----
}
