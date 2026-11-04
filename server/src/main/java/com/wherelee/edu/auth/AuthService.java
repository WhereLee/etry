package com.wherelee.edu.auth;

import com.wherelee.edu.auth.dto.ChangePasswordRequest;
import com.wherelee.edu.auth.dto.LoginRequest;
import com.wherelee.edu.auth.dto.LoginResponse;
import com.wherelee.edu.auth.dto.UserInfo;
import com.wherelee.edu.common.BizException;
import com.wherelee.edu.security.JwtService;
import com.wherelee.edu.user.User;
import com.wherelee.edu.user.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
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