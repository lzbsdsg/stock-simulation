package com.lzbsdsg.stocksimulation.auth.infrastructure.persistence;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lzbsdsg.stocksimulation.auth.domain.entity.User;
import com.lzbsdsg.stocksimulation.auth.domain.repository.UserRepository;
import com.lzbsdsg.stocksimulation.auth.infrastructure.converter.UserConverter;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** UserRepository 实现（MyBatis-Plus） */
@Repository
public class UserRepositoryImpl implements UserRepository {

  private final UserMapper userMapper;
  private final UserConverter userConverter;

  public UserRepositoryImpl(UserMapper userMapper, UserConverter userConverter) {
    this.userMapper = userMapper;
    this.userConverter = userConverter;
  }

  @Override
  public Optional<User> findByEmail(String email) {
    UserDO userDO =
        userMapper.selectOne(new LambdaQueryWrapper<UserDO>().eq(UserDO::getEmail, email));
    return Optional.ofNullable(userDO).map(userConverter::toDomain);
  }

  @Override
  public Optional<User> findById(Long id) {
    UserDO userDO = userMapper.selectById(id);
    return Optional.ofNullable(userDO).map(userConverter::toDomain);
  }

  @Override
  public User save(User user) {
    UserDO userDO = userConverter.toDO(user);
    if (userDO.getId() == null) {
      userMapper.insert(userDO);
    } else {
      userMapper.updateById(userDO);
    }
    return userConverter.toDomain(userDO);
  }

  @Override
  public void updateFailedAttempts(Long userId, int failedAttempts, Instant lockedUntil) {
    UserDO update = new UserDO();
    update.setId(userId);
    update.setFailedAttempts(failedAttempts);
    update.setLockedUntil(lockedUntil);
    if (lockedUntil != null) {
      update.setStatus("LOCKED");
    } else if (failedAttempts == 0) {
      update.setStatus("ACTIVE");
    }
    userMapper.updateById(update);
  }

  @Override
  public void updatePassword(Long userId, String newPasswordHash) {
    UserDO update = new UserDO();
    update.setId(userId);
    update.setPasswordHash(newPasswordHash);
    userMapper.updateById(update);
  }

  @Override
  public boolean existsByEmail(String email) {
    return userMapper.selectCount(new LambdaQueryWrapper<UserDO>().eq(UserDO::getEmail, email)) > 0;
  }
}
