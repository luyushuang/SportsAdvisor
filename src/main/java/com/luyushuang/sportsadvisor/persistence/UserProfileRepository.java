package com.luyushuang.sportsadvisor.persistence;

import com.luyushuang.sportsadvisor.entity.ProfileUpdate;
import com.luyushuang.sportsadvisor.entity.UserProfile;
import com.luyushuang.sportsadvisor.persistence.entity.UserProfilePO;
import com.luyushuang.sportsadvisor.persistence.mapper.UserProfileMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Repository
public class UserProfileRepository {

    private static final Logger log = LoggerFactory.getLogger(UserProfileRepository.class);
    private static final int MAX_RETRY = 3;

    private final UserProfileMapper mapper;

    public UserProfileRepository(UserProfileMapper mapper) {
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public Optional<UserProfile> findById(String userId) {
        return mapper.findByUserId(userId).map(this::toDomain);
    }

    /**
     * 合并并保存：整体在一个事务内完成。
     * 用悲观锁（FOR UPDATE）串行化并发写入，避免乐观锁重试的复杂性。
     */
    @Transactional
    public UserProfile mergeAndSave(String userId, ProfileUpdate update) {
        UserProfilePO existing = mapper.findByUserIdForUpdate(userId).orElse(null);

        if (existing == null) {
            // 首次创建
            UserProfilePO fresh = new UserProfilePO();
            fresh.setUserId(userId);
            applyUpdate(fresh, update);
            try {
                mapper.insert(fresh);
                return toDomain(fresh);
            } catch (org.springframework.dao.DuplicateKeyException e) {
                // 并发首次创建，重试一次读-改-写
                log.warn("并发首次创建画像，userId={}，重试", userId);
                return doMergeWithPessimisticLock(userId, update);
            }
        }

        applyUpdate(existing, update);
        mapper.updateWithVersion(existing);
        return toDomain(existing);
    }

    /**
     * 全量覆盖：用于用户表单主动更新画像的场景（支持清空字段）。
     */
    @Transactional
    public UserProfile save(UserProfile profile) {
        UserProfilePO po = toPO(profile);
        UserProfilePO existing = mapper.findByUserIdForUpdate(profile.userId()).orElse(null);

        if (existing == null) {
            mapper.insert(po);
        } else {
            po.setVersion(existing.getVersion());
            int affected = mapper.updateWithVersion(po);
            if (affected == 0) {
                // 并发冲突，重试一次
                retrySave(po);
            }
        }
        return toDomain(mapper.findByUserId(profile.userId()).orElseThrow());
    }

    private UserProfile doMergeWithPessimisticLock(String userId, ProfileUpdate update) {
        UserProfilePO existing = mapper.findByUserIdForUpdate(userId).orElseThrow();
        applyUpdate(existing, update);
        mapper.updateWithVersion(existing);
        return toDomain(existing);
    }

    private void retrySave(UserProfilePO po) {
        for (int i = 0; i < MAX_RETRY; i++) {
            UserProfilePO latest = mapper.findByUserIdForUpdate(po.getUserId()).orElseThrow();
            po.setVersion(latest.getVersion());
            if (mapper.updateWithVersion(po) > 0) {
                return;
            }
        }
        throw new IllegalStateException("画像更新冲突，重试 " + MAX_RETRY + " 次仍失败，userId=" + po.getUserId());
    }

    // ---- 领域对象 / PO 转换 ----

    private UserProfile toDomain(UserProfilePO po) {
        return new UserProfile(
                po.getUserId(), po.getAge(), po.getWeightKg(), po.getHeightCm(),
                po.getRunningExperience(), po.getWeeklyMileageKm(),
                po.getBestHalfMarathonTime(), po.getTrainingGoal(),
                po.getInjuryHistory() == null ? java.util.List.of() : po.getInjuryHistory(),
                po.getPreferredRunTime(), po.getPreferredTerrain()
        );
    }

    private UserProfilePO toPO(UserProfile p) {
        UserProfilePO po = new UserProfilePO();
        po.setUserId(p.userId());
        po.setAge(p.age());
        po.setWeightKg(p.weightKg());
        po.setHeightCm(p.heightCm());
        po.setRunningExperience(p.runningExperience());
        po.setWeeklyMileageKm(p.weeklyMileageKm());
        po.setBestHalfMarathonTime(p.bestHalfMarathonTime());
        po.setTrainingGoal(p.trainingGoal());
        po.setInjuryHistory(p.injuryHistory() == null
                ? new java.util.ArrayList<>() : new java.util.ArrayList<>(p.injuryHistory()));
        po.setPreferredRunTime(p.preferredRunTime());
        po.setPreferredTerrain(p.preferredTerrain());
        return po;
    }

    /**
     * 增量合并：只覆盖非 null 字段；injuryHistory 空列表代表"明确说了没有"，null 代表"没提到"。
     */
    private void applyUpdate(UserProfilePO po, ProfileUpdate u) {
        if (u.age() != null) po.setAge(u.age());
        if (u.weightKg() != null) po.setWeightKg(u.weightKg());
        if (u.heightCm() != null) po.setHeightCm(u.heightCm());
        if (u.runningExperience() != null) po.setRunningExperience(u.runningExperience());
        if (u.weeklyMileageKm() != null) po.setWeeklyMileageKm(u.weeklyMileageKm());
        if (u.bestHalfMarathonTime() != null) po.setBestHalfMarathonTime(u.bestHalfMarathonTime());
        if (u.trainingGoal() != null) po.setTrainingGoal(u.trainingGoal());
        if (u.injuryHistory() != null) {
            po.setInjuryHistory(new java.util.ArrayList<>(u.injuryHistory()));
        }
        if (u.preferredRunTime() != null) po.setPreferredRunTime(u.preferredRunTime());
        if (u.preferredTerrain() != null) po.setPreferredTerrain(u.preferredTerrain());
    }
}
