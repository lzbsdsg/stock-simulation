package com.lzbsdsg.stocksimulation.user.domain.repository;

import com.lzbsdsg.stocksimulation.user.domain.entity.UserProfile;
import java.util.Optional;

/** 用户资料仓储接口 */
public interface UserProfileRepository {

  Optional<UserProfile> findByUserId(Long userId);

  void save(UserProfile profile);

  void update(UserProfile profile);
}
