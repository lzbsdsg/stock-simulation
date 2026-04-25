package com.lzbsdsg.stocksimulation.user.application;

import com.lzbsdsg.stocksimulation.auth.domain.entity.User;
import com.lzbsdsg.stocksimulation.auth.domain.repository.UserRepository;
import com.lzbsdsg.stocksimulation.auth.domain.service.PasswordDomainService;
import com.lzbsdsg.stocksimulation.common.exception.BizException;
import com.lzbsdsg.stocksimulation.common.result.ErrorCode;
import com.lzbsdsg.stocksimulation.user.application.command.ChangePasswordCommand;
import com.lzbsdsg.stocksimulation.user.application.command.UpdateUserProfileCommand;
import com.lzbsdsg.stocksimulation.user.application.dto.UserProfileDTO;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 用户应用服务。 */
@Service
public class UserApplicationService {

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final PasswordDomainService passwordDomainService;

  public UserApplicationService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
    this.passwordDomainService = new PasswordDomainService();
  }

  @Transactional(readOnly = true)
  public UserProfileDTO getCurrentUser(Long userId) {
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new BizException(ErrorCode.USER_NOT_FOUND));
    return toProfileDTO(user);
  }

  @Transactional
  public UserProfileDTO updateProfile(Long userId, UpdateUserProfileCommand command) {
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new BizException(ErrorCode.USER_NOT_FOUND));
    user.setNickname(command.nickname());
    User saved = userRepository.save(user);
    return toProfileDTO(saved);
  }

  @Transactional
  public void changePassword(Long userId, ChangePasswordCommand command) {
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new BizException(ErrorCode.USER_NOT_FOUND));
    if (!passwordEncoder.matches(command.oldPassword(), user.getPasswordHash())) {
      throw new BizException(ErrorCode.AUTH_LOGIN_FAILED);
    }
    if (!passwordDomainService.isPasswordStrong(command.newPassword())) {
      throw new BizException(ErrorCode.AUTH_PASSWORD_TOO_WEAK);
    }
    userRepository.updatePassword(userId, passwordEncoder.encode(command.newPassword()));
  }

  private UserProfileDTO toProfileDTO(User user) {
    return new UserProfileDTO(
        user.getId(), user.getEmail(), user.getNickname(), user.getRole(), user.getStatus());
  }
}
