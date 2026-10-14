package com.luyushuang.sportsadvisor.repository;

import com.luyushuang.sportsadvisor.entity.UserProfile;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class UserProfileRepository {
    // 生产环境替换为 JPA / MyBatis 持久化
    private final ConcurrentHashMap<String, UserProfile> store = new ConcurrentHashMap<>();

    public Optional<UserProfile> findById(String userId) {
        return Optional.ofNullable(store.get(userId));
    }

    public UserProfile save(UserProfile profile) {
        store.put(profile.userId(), profile);
        return profile;
    }
}
