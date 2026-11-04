package com.wherelee.edu.auth;

import com.wherelee.edu.auth.dto.ChangePasswordRequest;
import com.wherelee.edu.auth.dto.LoginRequest;
import com.wherelee.edu.auth.dto.LoginResponse;
import com.wherelee.edu.auth.dto.UserInfo;
import com.wherelee.edu.security.ClientIpResolver;
import com.wherelee.edu.security.LoginThrottleService;
import com.wherelee.edu.aop.Measured;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final LoginThrottleService loginThrottleService;
    private final ClientIpResolver clientIpResolver;

    public AuthController(AuthService authService,
                          LoginThrottleService loginThrottleService,
                          ClientIpResolver clientIpResolver) {
        this.authService = authService;
        this.loginThrottleService = loginThrottleService;
        this.clientIpResolver = clientIpResolver;
    }

    @PostMapping("/login")
    @Measured("auth.login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        String clientIp = clientIpResolver.resolve(httpRequest);
        loginThrottleService.check(request.username(), clientIp);
        LoginResponse response;
        try {
            response = authService.login(request);
        } catch (RuntimeException e) {
            loginThrottleService.recordFailure(request.username(), clientIp);
            throw e;
        }
        loginThrottleService.clear(request.username(), clientIp);
        return response;
    }

    @GetMapping("/me")
    public UserInfo me(@RequestAttribute("userId") Long userId) {
        return authService.me(userId);
    }

    @PutMapping("/password")
    public void changePassword(@RequestAttribute("userId") Long userId,
                               @Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(userId, request);
    }
}
