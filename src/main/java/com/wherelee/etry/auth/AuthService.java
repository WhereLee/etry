package com.wherelee.etry.auth;

import com.wherelee.etry.common.BusinessException;
import com.wherelee.etry.common.ErrorCode;
import com.wherelee.etry.config.EtryProperties;
import com.wherelee.etry.config.MailProperties;
import com.wherelee.etry.user.User;
import com.wherelee.etry.user.UserRepository;
import com.wherelee.etry.user.UserStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final MailCodeService mailCodeService;
    private final LoginRateLimiter loginRateLimiter;
    private final EtryProperties properties;
    private final MailProperties mailProperties;

    /**
     * 注册。邮箱已存在时一律返回成功，不暴露“该邮箱是否已注册”。
     * 已存在但未验证的账号允许覆盖密码并重发验证码。
     *
     * @return 是否需要走邮箱验证；etry.mail.verify-register=false 时为 false，账号直接生效
     */
    @Transactional
    public boolean register(String email, String password, String nickname) {
        String normalized = normalize(email);
        boolean verifyRequired = mailProperties.isVerifyRegister();

        Optional<User> existing = userRepository.findByEmail(normalized);
        if (existing.isPresent() && existing.get().getStatus() == UserStatus.ACTIVE) {
            log.info("注册请求命中已激活邮箱，静默返回 normalized={}", normalized);
            return verifyRequired;
        }

        User user = existing.orElseGet(() -> {
            User created = new User();
            created.setEmail(normalized);
            created.setQuotaBytes(properties.getQuota().getDefaultBytes());
            created.setUsedBytes(0);
            created.setReservedBytes(0);
            created.setTokenVersion(0);
            return created;
        });
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setNickname(nickname.trim());
        user.setStatus(verifyRequired ? UserStatus.PENDING_VERIFY : UserStatus.ACTIVE);
        userRepository.save(user);

        if (verifyRequired) {
            mailCodeService.issue(MailScene.REGISTER, normalized);
        } else {
            log.info("邮箱验证开关关闭，注册直接生效 normalized={}", normalized);
        }
        return verifyRequired;
    }

    @Transactional
    public void verifyEmail(String email, String code) {
        String normalized = normalize(email);
        User user = userRepository.findByEmail(normalized)
                .orElseThrow(() -> new BusinessException(ErrorCode.CODE_EXPIRED));
        mailCodeService.verify(MailScene.REGISTER, normalized, code);
        user.setStatus(UserStatus.ACTIVE);
        userRepository.save(user);
    }

    /**
     * 重发验证码。只有处于待验证状态的账号才真的发信，
     * 其它情况静默返回，同样是为了不暴露账号是否存在。
     */
    public void resendVerifyCode(String email) {
        String normalized = normalize(email);
        User user = userRepository.findByEmail(normalized).orElse(null);
        if (user == null || user.getStatus() != UserStatus.PENDING_VERIFY) {
            return;
        }
        mailCodeService.issue(MailScene.REGISTER, normalized);
    }

    @Transactional
    public User login(String email, String password, String ip) {
        String normalized = normalize(email);
        loginRateLimiter.checkIp(ip);
        loginRateLimiter.checkAccountLocked(normalized);

        User user = userRepository.findByEmail(normalized).orElse(null);
        if (user == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
            loginRateLimiter.recordFailure(normalized);
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.EMAIL_NOT_VERIFIED);
        }
        loginRateLimiter.clearFailures(normalized);
        return user;
    }

    /**
     * 找回密码：无论邮箱是否存在都返回成功。
     * 内部的冷却、额度异常一律吞掉，只记日志。
     */
    public void forgotPassword(String email) {
        String normalized = normalize(email);
        User user = userRepository.findByEmail(normalized).orElse(null);
        if (user == null) {
            log.info("找回密码请求命中未注册邮箱，静默返回");
            return;
        }
        try {
            mailCodeService.issue(MailScene.RESET, normalized);
        } catch (BusinessException e) {
            log.info("找回密码发信被拒 code={} normalized={}", e.getErrorCode(), normalized);
        }
    }

    /**
     * 重置密码：校验码通过后改密并踢掉所有旧登录。
     * 如果账号还处于待验证状态，说明邮箱所有权已被证明，直接激活。
     */
    @Transactional
    public void resetPassword(String email, String code, String newPassword) {
        String normalized = normalize(email);
        User user = userRepository.findByEmail(normalized)
                .orElseThrow(() -> new BusinessException(ErrorCode.CODE_EXPIRED));
        mailCodeService.verify(MailScene.RESET, normalized, code);

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setTokenVersion(user.getTokenVersion() + 1);
        if (user.getStatus() == UserStatus.PENDING_VERIFY) {
            user.setStatus(UserStatus.ACTIVE);
        }
        userRepository.save(user);
        loginRateLimiter.clearFailures(normalized);
    }

    private String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
