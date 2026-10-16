package com.luyushuang.sportsadvisor.persistence.mapper;

import com.luyushuang.sportsadvisor.persistence.entity.UserProfilePO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Optional;

@Mapper
public interface UserProfileMapper {

    /** 普通查询 */
    Optional<UserProfilePO> findByUserId(@Param("userId") String userId);

    /** 悲观锁查询：SELECT ... FOR UPDATE */
    Optional<UserProfilePO> findByUserIdForUpdate(@Param("userId") String userId);

    /** 插入新记录 */
    int insert(UserProfilePO po);

    /** 基于 version 的乐观锁更新，返回受影响行数 */
    int updateWithVersion(UserProfilePO po);
}
