package com.luyushuang.sportsadvisor.agent;

import com.luyushuang.sportsadvisor.entity.ProfileUpdate;
import com.luyushuang.sportsadvisor.entity.UserProfile;
import com.luyushuang.sportsadvisor.repository.UserProfileRepository;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class ProfileAgent {

    private final ChatClient chatClient;

    private final UserProfileRepository profileRepository;

    public ProfileAgent(UserProfileRepository profileRepository, ChatClient.Builder builder) {
        this.profileRepository = profileRepository;
        this.chatClient = builder.build();
    }

    /**
     * 将用户画像格式化为自然语言，用于注入 System Prompt
     */
    public String buildProfileContext(String userId) {
        return profileRepository.findById(userId)
                .map(profile -> String.format("""
                                当前用户画像：
                                - 年龄：%d岁，体重：%.1fkg，身高：%dcm
                                - 跑龄：%s，周跑量：%.1fkm
                                - 半马最好成绩：%s
                                - 训练目标：%s
                                - 伤病历史：%s
                                - 偏好跑步时段：%s，偏好路面：%s
                                """,
                        profile.age(), profile.weightKg(), profile.heightCm(),
                        profile.runningExperience(), profile.weeklyMileageKm(),
                        profile.bestHalfMarathonTime() != null ? profile.bestHalfMarathonTime() : "无",
                        profile.trainingGoal(),
                        profile.injuryHistory().isEmpty() ? "无" : String.join("、", profile.injuryHistory()),
                        profile.preferredRunTime(), profile.preferredTerrain()))
                .orElse("暂无用户画像信息，请先向用户了解基本情况。");
    }

    /**
     * 从对话中自动提取用户画像信息（简化版）
     * 生产环境可以用 LLM 做结构化提取
     */
    public void updateProfileFromConversation(String userId, String userMessage) {
        ProfileUpdate update = chatClient.prompt()
                .system("""
                        从用户的消息中提取跑步相关的个人信息。
                        只提取消息中明确提到的字段，未提到的字段返回 null。
                        不要推测或编造信息。
                        """)
                .user(userMessage)
                .call()
                .entity(ProfileUpdate.class);

        if (update == null) {
            return;
        }
        // 与现有画像合并（仅更新非 null 字段）
        profileRepository.findById(userId).ifPresent(existing -> {
            UserProfile merged = new UserProfile(
                    userId,
                    update.age() != null ? update.age() : existing.age(),
                    update.weightKg() != null ? update.weightKg() : existing.weightKg(),
                    // ... 其余字段同理，仅在新值非 null 时覆盖
                    update.heightCm() != null ? update.heightCm() : existing.heightCm(),
                    update.runningExperience() != null ? update.runningExperience() : existing.runningExperience(),
                    update.weeklyMileageKm() != null ? update.weeklyMileageKm() : existing.weeklyMileageKm(),
                    update.bestHalfMarathonTime() != null ? update.bestHalfMarathonTime() : existing.bestHalfMarathonTime(),
                    update.trainingGoal() != null ? update.trainingGoal() : existing.trainingGoal(),
                    update.injuryHistory() != null && !update.injuryHistory().isEmpty()
                            ? update.injuryHistory() : existing.injuryHistory(),
                    update.preferredRunTime() != null ? update.preferredRunTime() : existing.preferredRunTime(),
                    update.preferredTerrain() != null ? update.preferredTerrain() : existing.preferredTerrain()
            );
            profileRepository.save(merged);
        });
    }
}
