package com.wherelee.trade.auth;

import com.wherelee.trade.auth.dto.LoginResponse;
import com.wherelee.trade.auth.dto.UserVO;
import com.wherelee.trade.common.BizException;
import com.wherelee.trade.common.ErrorCode;
import com.wherelee.trade.point.PointAccount;
import com.wherelee.trade.point.PointAccountMapper;
import com.wherelee.trade.user.User;
import com.wherelee.trade.user.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserMapper userMapper;
    private final PointAccountMapper pointAccountMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final TokenBlacklistService blacklistService;

    public LoginResponse login(String username, String password) {
        User user = userMapper.findByUsername(username);
        if (user == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new BizException(ErrorCode.LOGIN_FAILED);
        }
        return issueToken(user);
    }

    /**
     * 注册并直接登录。同时开一个积分账户，余额为 0 —— M0 没有充值功能，
     * 新注册用户暂时无法下单，验收请用演示账号 buyer。
     */
    @Transactional
    public LoginResponse register(String username, String password, String nickname) {
        if (userMapper.findByUsername(username) != null) {
            throw new BizException(ErrorCode.USERNAME_EXISTS);
        }
        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setNickname(nickname);
        user.setRole(User.ROLE_USER);
        user.setTokenVersion(0);
        userMapper.insert(user);

        PointAccount account = new PointAccount();
        account.setUserId(user.getId());
        account.setBalance(0L);
        pointAccountMapper.insert(account);

        return issueToken(user);
    }

    /**
     * 退出当前设备：把本 token 的 jti 写入黑名单，直到它自然过期。
     * 其他设备签发的 token 不受影响。
     */
    public void logout() {
        UserContext.Principal principal = UserContext.get();
        if (principal == null || principal.jti() == null) {
            return;
        }
        Duration remaining = Duration.between(Instant.now(), principal.expiresAt());
        blacklistService.blacklist(principal.jti(), remaining);
    }

    /**
     * 修改密码：把令牌版本 +1，该用户此前签发的所有 token（所有设备）立即失效。
     * 当前这次请求仍然成功返回，前端需引导用户重新登录。
     */
    @Transactional
    public void changePassword(String oldPassword, String newPassword) {
        Long userId = UserContext.requireUserId();
        User user = userMapper.findById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        if (!passwordEncoder.matches(oldPassword, user.getPasswordHash())) {
            throw new BizException(ErrorCode.OLD_PASSWORD_WRONG);
        }
        userMapper.updatePasswordAndBumpVersion(userId, passwordEncoder.encode(newPassword));
    }

    public UserVO me() {
        Long userId = UserContext.requireUserId();
        User user = userMapper.findById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        return new UserVO(user.getId(), user.getUsername(), user.getNickname(), user.getRole());
    }

    private LoginResponse issueToken(User user) {
        String token = jwtService.issue(user.getId(), user.getUsername(), user.getRole(), user.getTokenVersion());
        return new LoginResponse(token, user.getId(), user.getNickname(), user.getRole());
    }
}
