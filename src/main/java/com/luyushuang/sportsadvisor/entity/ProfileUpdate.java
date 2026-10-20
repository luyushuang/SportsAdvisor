package com.luyushuang.sportsadvisor.entity;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

public record ProfileUpdate(
        @JsonPropertyDescription("用户年龄，整数。仅当消息中明确提到年龄时填写，否则为 null")
        Integer age,

        @JsonPropertyDescription("体重，单位 kg。仅当消息中明确提到体重时填写，否则为 null")
        Double weightKg,

        @JsonPropertyDescription("身高，单位 cm。仅当消息中明确提到身高时填写，否则为 null")
        Integer heightCm,

        @JsonPropertyDescription("跑龄描述，如'2年'、'新手'。仅当消息中明确提到跑龄时填写，否则为 null")
        String runningExperience,

        @JsonPropertyDescription("周跑量，单位 km。仅当消息中明确提到周跑量时填写，否则为 null")
        Double weeklyMileageKm,

        @JsonPropertyDescription("半马最好成绩，如'2小时10分'。仅当消息中明确提到成绩时填写，否则为 null")
        String bestHalfMarathonTime,

        @JsonPropertyDescription("训练目标，如'半马破2'、'完赛'。仅当消息中明确提到目标时填写，否则为 null")
        String trainingGoal,

        @JsonPropertyDescription("伤病历史列表。仅当消息中明确提到伤病时填写；用户明确说'没有伤病'时返回空列表 []；未提到时返回 null")
        List<String> injuryHistory,

        @JsonPropertyDescription("偏好跑步时段，如'早晨'、'傍晚'。仅当消息中明确提到时填写，否则为 null")
        String preferredRunTime,

        @JsonPropertyDescription("偏好路面，如'公路'、'跑道'、'越野'。仅当消息中明确提到时填写，否则为 null")
        String preferredTerrain
) {}