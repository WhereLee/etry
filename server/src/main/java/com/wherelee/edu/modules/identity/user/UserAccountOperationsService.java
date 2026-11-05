package com.wherelee.edu.modules.identity.user;

import com.wherelee.edu.modules.identity.api.UserAccount;
import com.wherelee.edu.modules.identity.api.UserAccountOperations;
import com.wherelee.edu.modules.platform.common.BizException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 账号写操作应用服务，集中身份模块的账号生命周期规则。
 */
@Service
public class UserAccountOperationsService implements UserAccountOperations {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserAccountOperationsService(UserRepository userRepository,
                                        PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public Long createStudent(String username, String rawPassword, String nickname) {
        if (userRepository.findByUsername(username).isPresent()) {
            throw BizException.badRequest("用户名已存在");
        }
        return userRepository.create(
                username,
                passwordEncoder.encode(rawPassword),
                nickname,
                "STUDENT",
                true
        );
    }

    @Override
    @Transactional
    public void changeStatus(Long operatorId, Long userId, String status) {
        if (operatorId.equals(userId)) {
            throw BizException.badRequest("不能修改自己的账号状态");
        }
        User target = requireUser(userId);
        if (status.equals(target.status())) {
            return;
        }
        if ("DISABLED".equals(status)) {
            userRepository.disable(userId);
        } else if ("ACTIVE".equals(status)) {
            userRepository.enable(userId);
        } else {
            throw BizException.badRequest("状态只能是 ACTIVE 或 DISABLED");
        }
    }

    @Override
    @Transactional
    public void resetPassword(Long operatorId, Long userId, String rawPassword) {
        if (operatorId.equals(userId)) {
            throw BizException.badRequest("请通过修改密码修改自己的密码");
        }
        requireUser(userId);
        userRepository.resetPassword(userId, passwordEncoder.encode(rawPassword));
    }

    @Override
    public UserAccount require(Long userId) {
        User user = requireUser(userId);
        return new UserAccount(user.id(), user.username(), user.nickname(), user.role(), user.status());
    }

    private User requireUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> BizException.notFound("用户不存在"));
    }
}