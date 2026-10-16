package com.luyushuang.sportsadvisor.repository;

import com.luyushuang.sportsadvisor.entity.ProfileUpdate;
import com.luyushuang.sportsadvisor.entity.UserProfile;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class UserProfileRepository {

    // 生产环境替换为 JPA / MyBatis 持久化
    private final ConcurrentHashMap<String, UserProfile> store = new ConcurrentHashMap<>();

    public Optional<UserProfile> findById(String userId) {
        return Optional.ofNullable(store.get(userId));
    }

    /**
     * 合并并保存（原子操作）。
     * 同一用户的并发提取会被串行化，保证不丢更新。
     */
    public UserProfile mergeAndSave(String userId, ProfileUpdate update) {
        return store.compute(userId, (id, existing) -> {
            if (existing == null) {
                return toNewProfile(userId, update);
            }
            return merge(existing, update);
        });
    }

    /**
     * 直接保存完整画像（用于用户主动更新场景）
     */
    public UserProfile save(UserProfile profile) {
        store.put(profile.userId(), profile);
        return profile;
    }

    private UserProfile toNewProfile(String userId, ProfileUpdate u) {
        return new UserProfile(
                userId,
                u.age(), u.weightKg(), u.heightCm(),
                u.runningExperience(), u.weeklyMileageKm(),
                u.bestHalfMarathonTime(), u.trainingGoal(),
                u.injuryHistory() != null ? u.injuryHistory() : List.of(),
                u.preferredRunTime(), u.preferredTerrain()
        );
    }

    private UserProfile merge(UserProfile existing, ProfileUpdate u) {
        return new UserProfile(
                existing.userId(),
                // 标量字段：新值非 null 才覆盖
                u.age() != null ? u.age() : existing.age(),
                u.weightKg() != null ? u.weightKg() : existing.weightKg(),
                u.heightCm() != null ? u.heightCm() : existing.heightCm(),
                u.runningExperience() != null ? u.runningExperience() : existing.runningExperience(),
                u.weeklyMileageKm() != null ? u.weeklyMileageKm() : existing.weeklyMileageKm(),
                u.bestHalfMarathonTime() != null ? u.bestHalfMarathonTime() : existing.bestHalfMarathonTime(),
                u.trainingGoal() != null ? u.trainingGoal() : existing.trainingGoal(),
                // 列表字段：空列表代表"明确说了没有"，null 代表"没提到"
                u.injuryHistory() != null ? u.injuryHistory() : existing.injuryHistory(),
                u.preferredRunTime() != null ? u.preferredRunTime() : existing.preferredRunTime(),
                u.preferredTerrain() != null ? u.preferredTerrain() : existing.preferredTerrain()
        );
    }
}
