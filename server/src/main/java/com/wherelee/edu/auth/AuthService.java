package com.wherelee.edu.auth;

import com.wherelee.edu.auth.dto.LoginResponse;
import com.wherelee.edu.auth.dto.PasswordChangeResult;
import com.wherelee.edu.auth.dto.RegisterRequest;
import com.wherelee.edu.auth.dto.UserMeVO;
import com.wherelee.edu.common.BizException;
import com.wherelee.edu.common.ErrorCode;
import com.wherelee.edu.config.ConfigService;
import com.wherelee.edu.rbac.PermissionService;
import com.wherelee.edu.security.JwtService;
import com.wherelee.edu.security.LoginAttemptService;
import com.wherelee.edu.security.TokenBlacklistService;
import com.wherelee.edu.security.UserContext;
import com.wherelee.edu.user.Role;
import com.wherelee.edu.user.RoleMapper;
import com.wherelee.edu.user.User;
import com.wherelee.edu.user.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    /** 后台「系统配置」里的注册开关，关掉后注册接口直接拒绝 */
    private static final String CONFIG_REGISTER_ENABLED = "register.enabled";

    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final TokenBlacklistService blacklistService;
    private final LoginAttemptService loginAttemptService;
    private final PermissionService permissionService;
    private final ConfigService configService;
    private final AuthProperties authProperties;

    /**
     * 注册即登录。新用户默认分配 STUDENT 角色，
     * 讲师身份需要另外提交申请并由管理员审核，这里不给。
     */
    @Transactional
    public LoginResponse register(RegisterRequest request) {
        // 默认关闭：内部平台以管理员开号为主，要放开在 t_config 的 register.enabled 改
        if (!configService.isEnabled(CONFIG_REGISTER_ENABLED, false)) {
            throw BizException.of(ErrorCode.REGISTER_DISABLED);
        }
        if (userMapper.findByUsername(request.username()) != null) {
            // 明说「已存在」等于把注册接口变成账号枚举工具，
            // 登录那边特意合并了「账号不存在」和「密码错误」，这里不能又开一个口子
            throw BizException.of(authProperties.revealUsernameConflict()
                    ? ErrorCode.USERNAME_EXISTS : ErrorCode.REGISTER_CONFLICT);
        }

        User user = new User();
        user.setUsername(request.username());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setNickname(request.nickname());
        user.setStatus(User.STATUS_ACTIVE);
        // 必须显式赋 0：insert 后对象不会回填数据库默认值，签发 token 时会拆箱 NPE
        user.setTokenVersion(0);
        user.setMustChangePassword(false);
        userMapper.insert(user);

        Role student = roleMapper.findByCode(Role.CODE_STUDENT);
        if (student == null) {
            throw new IllegalStateException("内置角色 STUDENT 不存在，请检查 V2 种子数据");
        }
        roleMapper.bindRole(user.getId(), student.getId());

        log.info("新用户注册 username={} userId={}", user.getUsername(), user.getId());
        return toLoginResponse(user, List.of(Role.CODE_STUDENT), false);
    }

    public LoginResponse login(String username, String password, String ip) {
        if (loginAttemptService.isLocked(username, ip)) {
            log.warn("登录被锁定 username={} ip={}", username, ip);
            throw BizException.of(ErrorCode.LOGIN_LOCKED);
        }

        User user = userMapper.findByUsername(username);
        if (user == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
            // 账号不存在与密码错误走同一条路：既记录失败次数，也统一报同一个错误，避免被用来探测账号是否存在
            loginAttemptService.recordFailure(username, ip);
            throw BizException.of(ErrorCode.LOGIN_FAILED);
        }
        if (!user.isActive()) {
            throw BizException.of(ErrorCode.ACCOUNT_DISABLED);
        }

        loginAttemptService.clear(username, ip);
        List<String> roles = roleMapper.findCodesByUserId(user.getId());
        return toLoginResponse(user, roles, user.needsPasswordChange());
    }

    /**
     * 退出当前设备：把本 token 的 jti 拉黑，直到它自然过期。其他设备不受影响。
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
     * 修改密码：令牌版本 +1，该用户所有设备的 token 立即失效。
     */
    @Transactional
    public PasswordChangeResult changePassword(String oldPassword, String newPassword) {
        Long userId = UserContext.requireUserId();
        User user = userMapper.findById(userId);
        if (user == null) {
            throw BizException.of(ErrorCode.UNAUTHORIZED);
        }
        if (!passwordEncoder.matches(oldPassword, user.getPasswordHash())) {
            throw BizException.of(ErrorCode.OLD_PASSWORD_WRONG);
        }
        userMapper.updatePasswordAndBumpVersion(userId, passwordEncoder.encode(newPassword));
        log.info("用户修改密码 userId={}", userId);
        // 版本号已变，刚才这个 token 也失效了，前端必须重新登录
        return new PasswordChangeResult(true);
    }

    public UserMeVO me() {
        Long userId = UserContext.requireUserId();
        User user = userMapper.findById(userId);
        if (user == null) {
            throw BizException.of(ErrorCode.UNAUTHORIZED);
        }
        List<String> roles = roleMapper.findCodesByUserId(userId);
        List<String> permissions = permissionService.currentPermissions().stream().sorted().toList();
        return new UserMeVO(user.getId(), user.getUsername(), user.getNickname(), user.getStatus(), roles,
                permissions);
    }

    private LoginResponse toLoginResponse(User user, List<String> roles, boolean mustChangePassword) {
        String token = jwtService.issue(user.getId(), user.getTokenVersion());
        return new LoginResponse(token, user.getId(), user.getUsername(), user.getNickname(), roles,
                mustChangePassword);
    }
}
