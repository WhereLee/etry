package com.wherelee.material.modules.identity.auth;

import com.wherelee.material.modules.identity.auth.dto.ChangePasswordRequest;
import com.wherelee.material.modules.identity.auth.dto.LoginRequest;
import com.wherelee.material.modules.identity.auth.dto.LoginResponse;
import com.wherelee.material.modules.identity.auth.dto.RegisterRequest;
import com.wherelee.material.modules.identity.auth.dto.UserInfo;
import com.wherelee.material.modules.identity.rbac.RbacRepository;
import com.wherelee.material.modules.platform.common.BizException;
import com.wherelee.material.modules.identity.security.JwtService;
import com.wherelee.material.modules.identity.user.User;
import com.wherelee.material.modules.identity.user.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RbacRepository rbacRepository;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                       JwtService jwtService, RbacRepository rbacRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.rbacRepository = rbacRepository;
    }

    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.username().trim())
                .filter(User::isActive)
                .orElse(null);
        if (user == null || !passwordEncoder.matches(request.password(), user.passwordHash())) {
            throw BizException.unauthorized("账号或密码不正确");
        }
        return new LoginResponse(jwtService.createToken(user), toInfo(user), user.mustChangePassword());
    }

    /** 买家自助注册：legacy 角色列沿用 STUDENT 兼容旧学习端，RBAC 授 BUYER；注册即登录，无需首改密。 */
    @Transactional
    public LoginResponse register(RegisterRequest request) {
        String username = request.username().trim();
        if (userRepository.findByUsername(username).isPresent()) {
            throw BizException.badRequest("用户名已存在");
        }
        String nickname = request.nickname() == null || request.nickname().isBlank()
                ? username : request.nickname().trim();
        Long userId = userRepository.create(username, passwordEncoder.encode(request.password()),
                nickname, "STUDENT", false);
        rbacRepository.grantRole(userId, "BUYER", null);
        User user = userRepository.findById(userId).orElseThrow();
        return new LoginResponse(jwtService.createToken(user), toInfo(user), false);
    }

    public UserInfo me(Long userId) {
        User user = userRepository.findById(userId)
                .filter(User::isActive)
                .orElseThrow(() -> BizException.unauthorized("登录已失效，请重新登录"));
        return toInfo(user);
    }

    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest request) {
        User user = userRepository.findById(userId)
                .filter(User::isActive)
                .orElseThrow(() -> BizException.unauthorized("登录已失效，请重新登录"));
        if (!passwordEncoder.matches(request.oldPassword(), user.passwordHash())) {
            throw BizException.badRequest("原密码不正确");
        }
        if (passwordEncoder.matches(request.newPassword(), user.passwordHash())) {
            throw BizException.badRequest("新密码不能与原密码相同");
        }
        userRepository.updatePassword(userId, passwordEncoder.encode(request.newPassword()));
    }

    private UserInfo toInfo(User user) {
        return new UserInfo(user.id(), user.username(), user.nickname(), user.role());
    }
}