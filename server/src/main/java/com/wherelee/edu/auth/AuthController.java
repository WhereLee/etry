package com.wherelee.edu.auth;

import com.wherelee.edu.audit.AuditLog;
import com.wherelee.edu.auth.dto.ChangePasswordRequest;
import com.wherelee.edu.auth.dto.LoginRequest;
import com.wherelee.edu.auth.dto.LoginResponse;
import com.wherelee.edu.auth.dto.PasswordChangeResult;
import com.wherelee.edu.auth.dto.RegisterRequest;
import com.wherelee.edu.auth.dto.UserMeVO;
import com.wherelee.edu.common.ApiResponse;
import com.wherelee.edu.common.web.ClientIpResolver;
import com.wherelee.edu.security.CurrentUser;
import com.wherelee.edu.security.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final ClientIpResolver clientIpResolver;

    @PostMapping("/register")
    public ApiResponse<LoginResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.ok(authService.register(request));
    }

    /**
     * 登录成功与失败都要留痕：失败请求会被切面记成 success=false，
     * 这是发现暴力破解的唯一线索。detail 里的密码字段已由切面脱敏。
     */
    @PostMapping("/login")
    @AuditLog(action = "USER_LOGIN", targetType = "USER")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request,
                                            HttpServletRequest httpRequest) {
        return ApiResponse.ok(authService.login(request.username(), request.password(),
                clientIpResolver.resolve(httpRequest)));
    }

    @PostMapping("/logout")
    @AuditLog(action = "USER_LOGOUT", targetType = "USER")
    public ApiResponse<Void> logout() {
        authService.logout();
        return ApiResponse.ok();
    }

    /** 改密后会返回 needRelogin=true：token_version 变了，刚才这个 token 也失效了 */
    @PutMapping("/password")
    @AuditLog(action = "PASSWORD_CHANGE", targetType = "USER")
    public ApiResponse<PasswordChangeResult> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        return ApiResponse.ok(authService.changePassword(request.oldPassword(), request.newPassword()));
    }

    @GetMapping("/me")
    public ApiResponse<UserMeVO> me(@CurrentUser UserContext.Principal principal) {
        return ApiResponse.ok(authService.me());
    }
}
