package com.luyushuang.sportsadvisor.agent;

import com.luyushuang.sportsadvisor.entity.ProfileUpdate;
import com.luyushuang.sportsadvisor.entity.UserProfile;
import com.luyushuang.sportsadvisor.obs.AgentMetrics;
import com.luyushuang.sportsadvisor.persistence.UserProfileRepository;
import com.luyushuang.sportsadvisor.util.JsonUtils;
import com.luyushuang.sportsadvisor.util.PromptLoggingAdvisor;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

@Slf4j
@Service
public class ProfileAgent {

    /**
     * 触发提取的关键词，避免无意义的 LLM 调用
     */
    private static final Pattern PROFILE_KEYWORDS = Pattern.compile(
            "岁|年龄|跑龄|跑量|公里|km|半马|全马|马拉松|伤病|膝盖|脚|跟腱|"
                    + "目标|成绩|配速|心率|体重|身高|晨跑|夜跑|跑道|公路|越野"
    );

    private final ChatClient extractionChatClient;
    private final UserProfileRepository profileRepository;

    private final AgentMetrics metrics;
    private final ObservationRegistry observationRegistry;

    public ProfileAgent(ChatClient.Builder builder,
                        UserProfileRepository profileRepository,
                        AgentMetrics metrics,
                        ObservationRegistry observationRegistry) {
        this.profileRepository = profileRepository;
        this.metrics = metrics;
        this.observationRegistry = observationRegistry;
        // 提取专用的 ChatClient：不挂工具、不挂记忆、不挂 RAG
        this.extractionChatClient = builder
                .defaultSystem("""
                        从用户的消息中提取跑步相关的个人信息。
                        
                        规则：
                        1. 只提取消息中明确提到的字段，未提到的字段返回 null
                        2. 不要推测、不要编造、不要从常识推断
                        3. 如果用户说"没有伤病"，injuryHistory 返回空列表 []
                        4. 如果用户说"我不确定"，对应字段返回 null
                        5. 数值字段必须能从消息中直接读出，不要估算
                        
                        示例：
                        输入："我30岁，跑龄2年，周跑量30公里"
                        输出：{age: 30, runningExperience: "2年", weeklyMileageKm: 30.0, 其余为 null}
                        """)
                .defaultOptions(ChatOptions.builder().temperature(0.0))
                .defaultAdvisors(new PromptLoggingAdvisor())
                .build();
    }

    // ==================== 提取 + 合并 + 持久化 ====================

    /**
     * 一站式入口：提取 + 合并 + 持久化。
     * 如果消息中不包含画像关键词，快速跳过，不调用 LLM。
     */
    public UserProfile extractAndUpdate(String userId, String userMessage) {
        if (userMessage == null || !PROFILE_KEYWORDS.matcher(userMessage).find()) {
            metrics.recordProfileExtractionSkipped();
            return getProfile(userId);
        }

        return Observation.createNotStarted("sports.profile.extract", observationRegistry)
                .lowCardinalityKeyValue("sports.agent", "profile")
                .highCardinalityKeyValue("sports.user.id", userId)
                .observe(() -> {
                    long start = System.currentTimeMillis();
                    try {
                        ProfileUpdate update = extractFromConversation(userMessage);
                        int fieldCount = countNonNullFields(update);

                        UserProfile merged = profileRepository.mergeAndSave(userId, update);

                        long duration = System.currentTimeMillis() - start;
                        metrics.recordProfileExtraction(duration, fieldCount);
                        return merged;

                    } catch (Exception e) {
                        metrics.recordProfileExtractionFailed();
                        log.warn("画像提取失败，userId={}", userId, e);
                        return getProfile(userId);
                    }
                });
    }

    /**
     * 异步版本：用于对话结束后的持续积累，不阻塞用户响应。
     */
    @Async("profileExtractionExecutor")
    public CompletableFuture<UserProfile> extractAndUpdateAsync(String userId, String userMessage) {
        return CompletableFuture.completedFuture(extractAndUpdate(userId, userMessage));
    }

    // ==================== 用户主动更新 ====================

    /**
     * 用户通过表单或显式指令直接更新画像。
     */
    public UserProfile updateProfile(String userId, UserProfile profile) {
        return profileRepository.save(profile);
    }

    // ==================== 读取 ====================
    /**
     * 读取画像并格式化为自然语言，用于注入 System Prompt。
     */
    public String buildProfileContext(String userId) {
        return profileRepository.findById(userId)
                .map(this::formatProfile)
                .orElse("暂无用户画像信息，请先向用户了解基本情况。");
    }

    /**
     * 读取原始画像对象，供编排器判断是否足够生成计划。
     */
    private UserProfile getProfile(String userId) {
        return profileRepository.findById(userId).orElse(null);
    }

    private String formatProfile(UserProfile p) {
        return String.format("""
                        当前用户画像：
                        - 年龄：%s，体重：%skg，身高：%scm
                        - 跑龄：%s，周跑量：%skm
                        - 半马最好成绩：%s
                        - 训练目标：%s
                        - 伤病历史：%s
                        - 偏好跑步时段：%s，偏好路面：%s
                        """,
                p.age() != null ? p.age() + "岁" : "未知",
                p.weightKg() != null ? p.weightKg() : "未知",
                p.heightCm() != null ? p.heightCm() : "未知",
                p.runningExperience() != null ? p.runningExperience() : "未知",
                p.weeklyMileageKm() != null ? p.weeklyMileageKm() : "未知",
                p.bestHalfMarathonTime() != null ? p.bestHalfMarathonTime() : "无",
                p.trainingGoal() != null ? p.trainingGoal() : "未知",
                p.injuryHistory().isEmpty() ? "无" : String.join("、", p.injuryHistory()),
                p.preferredRunTime() != null ? p.preferredRunTime() : "未知",
                p.preferredTerrain() != null ? p.preferredTerrain() : "未知"
        );
    }

    // ==================== 提取 ====================

    /**
     * 从单条用户消息中提取结构化信息（纯函数，不涉及持久化）。
     */
    private ProfileUpdate extractFromConversation(String userMessage) {
        ProfileUpdate update = extractionChatClient.prompt()
                .user(userMessage)
                .call()
                .entity(ProfileUpdate.class);
        log.info("提取到的用户信息：{}", JsonUtils.toJson(update));
        assert update != null;
        return sanitize(update);
    }

    /**
     * 数值校验：丢弃明显越界的异常值，防止 LLM 提取错误污染画像。
     */
    private ProfileUpdate sanitize(ProfileUpdate u) {
        boolean anyInvalid = !validInt(u.age(), 5, 120)
                || !validDouble(u.weightKg(), 20, 300)
                || !validInt(u.heightCm(), 100, 250)
                || !validDouble(u.weeklyMileageKm(), 0, 500);

        if (anyInvalid) {
            metrics.recordProfileValidationFailed();
        }

        return new ProfileUpdate(
                validInt(u.age(), 5, 120) ? u.age() : null,
                validDouble(u.weightKg(), 20, 300) ? u.weightKg() : null,
                validInt(u.heightCm(), 100, 250) ? u.heightCm() : null,
                u.runningExperience(),
                validDouble(u.weeklyMileageKm(), 0, 500) ? u.weeklyMileageKm() : null,
                u.bestHalfMarathonTime(),
                u.trainingGoal(),
                u.injuryHistory(),
                u.preferredRunTime(),
                u.preferredTerrain()
        );
    }

    private boolean validInt(Integer v, int min, int max) {
        return v == null || (v >= min && v <= max);
    }

    private boolean validDouble(Double v, double min, double max) {
        return v == null || (v >= min && v <= max);
    }

    private int countNonNullFields(ProfileUpdate update) {
        int count = 0;
        if (update.age() != null) count++;
        if (update.weightKg() != null) count++;
        if (update.heightCm() != null) count++;
        if (update.runningExperience() != null) count++;
        if (update.weeklyMileageKm() != null) count++;
        if (update.bestHalfMarathonTime() != null) count++;
        if (update.trainingGoal() != null) count++;
        if (update.injuryHistory() != null && !update.injuryHistory().isEmpty()) count++;
        if (update.preferredRunTime() != null) count++;
        if (update.preferredTerrain() != null) count++;
        return count;
    }
}
