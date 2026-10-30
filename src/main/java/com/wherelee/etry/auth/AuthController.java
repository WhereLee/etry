package com.wherelee.etry.auth;

import com.wherelee.etry.auth.dto.AuthDtos;
import com.wherelee.etry.common.ApiResponse;
import com.wherelee.etry.user.User;
import com.wherelee.etry.user.UserService;
import com.wherelee.etry.user.UserView;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final UserService userService;
    private final JwtService jwtService;
    private final TokenBlacklist tokenBlacklist;
    private final TokenCookieSupport cookieSupport;

    @PostMapping("/register")
    public ApiResponse<AuthDtos.RegisterResponse> register(@Valid @RequestBody AuthDtos.RegisterRequest request) {
        boolean needVerify = authService.register(request.email(), request.password(), request.nickname());
        return ApiResponse.ok(new AuthDtos.RegisterResponse(needVerify));
    }

    @PostMapping("/verify-email")
    public ApiResponse<Void> verifyEmail(@Valid @RequestBody AuthDtos.VerifyEmailRequest request) {
        authService.verifyEmail(request.email(), request.code());
        return ApiResponse.ok();
    }

    @PostMapping("/verify-email/resend")
    public ApiResponse<Void> resendVerifyCode(@Valid @RequestBody AuthDtos.ResendRequest request) {
        authService.resendVerifyCode(request.email());
        return ApiResponse.ok();
    }

    @PostMapping("/login")
    public ApiResponse<UserView> login(@Valid @RequestBody AuthDtos.LoginRequest request,
                                       HttpServletRequest httpRequest,
                                       HttpServletResponse httpResponse) {
        User user = authService.login(request.email(), request.password(), clientIp(httpRequest));
        cookieSupport.write(httpResponse, user);
        return ApiResponse.ok(userService.view(user));
    }

    /**
     * 登出：把当前 token 的 jti 拉黑，再清 cookie。
     * 只清 cookie 的话，被复制的 token 还能继续用。
     */
    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        String token = cookieSupport.read(request);
        if (token != null) {
            Claims claims = jwtService.parse(token);
            if (claims != null && claims.getExpiration() != null) {
                Duration ttl = Duration.between(Instant.now(), claims.getExpiration().toInstant());
                tokenBlacklist.add(claims.getId(), ttl);
            }
        }
        cookieSupport.clear(response);
        return ApiResponse.ok();
    }

    @PostMapping("/password/forgot")
    public ApiResponse<Void> forgotPassword(@Valid @RequestBody AuthDtos.ForgotRequest request) {
        authService.forgotPassword(request.email());
        return ApiResponse.ok();
    }

    @PostMapping("/password/reset")
    public ApiResponse<Void> resetPassword(@Valid @RequestBody AuthDtos.ResetRequest request) {
        authService.resetPassword(request.email(), request.code(), request.newPassword());
        return ApiResponse.ok();
    }

    @GetMapping("/me")
    public ApiResponse<UserView> me(@AuthenticationPrincipal Long userId) {
        return ApiResponse.ok(userService.view(userService.get(userId)));
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
