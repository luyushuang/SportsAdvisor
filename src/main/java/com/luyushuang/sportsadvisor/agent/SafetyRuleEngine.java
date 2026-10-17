package com.luyushuang.sportsadvisor.agent;

import com.luyushuang.sportsadvisor.entity.HalfMarathonPlan;
import com.luyushuang.sportsadvisor.entity.TrainingWeek;
import com.luyushuang.sportsadvisor.entity.UserProfile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class SafetyRuleEngine {

    public List<String> checkRules(HalfMarathonPlan plan, UserProfile profile) {
        List<String> blockers = new ArrayList<>();

        // 规则1：每周跑量增幅不超过 10%
//        for (int i = 1; i < plan.weeks().size(); i++) {
//            double prevMileage = calculateWeekMileage(plan.weeks().get(i - 1));
//            double currMileage = calculateWeekMileage(plan.weeks().get(i));
//            if (prevMileage > 0 && currMileage > prevMileage * 1.10) {
//                blockers.add(String.format(
//                        "第 %d 周跑量 %.1fkm，较上周增幅 %.0f%%，超过 10%% 安全线",
//                        i + 1, currMileage,
//                        (currMileage / prevMileage - 1) * 100));
//            }
//        }

        // 规则2：每周至少 1 天完全休息
        for (TrainingWeek week : plan.weeks()) {
            long restDays = week.days().stream()
                    .filter(d -> "Rest Day".equalsIgnoreCase(d.workoutType())
                            || (d.workoutType() != null && d.workoutType().contains("休息")))
                    .count();
            if (restDays == 0) {
                blockers.add(String.format("第 %d 周没有安排休息日", week.weekNumber()));
            }
        }

        // 规则3：伤病用户的强度限制
//        if (!profile.injuryHistory().isEmpty()) {
//            // 如果用户有膝伤，检查计划中是否有相关预防措施
//            boolean hasPrevention = plan.weeks().stream()
//                    .flatMap(w -> w.days().stream())
//                    .anyMatch(d -> d.notes() != null &&
//                            (d.notes().contains("拉伸") || d.notes().contains("力量") ||
//                                    d.notes().contains("预防")));
//            if (!hasPrevention) {
//                blockers.add("用户有伤病历史，但计划中未包含预防措施");
//            }
//        }

        return blockers;
    }

    private double calculateWeekMileage(TrainingWeek week) {
        return week.days().stream()
                .filter(d -> d.description() != null)
                .mapToDouble(d -> extractDistance(d.description()))
                .sum();
    }

    private double extractDistance(String description) {
        // 从描述中提取公里数，简化实现
        // 实际项目应使用正则或结构化字段
        return 0.0;
    }
}
