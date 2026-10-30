package com.wherelee.edu.auth;

import com.wherelee.edu.auth.dto.ChangePasswordRequest;
import com.wherelee.edu.auth.dto.LoginRequest;
import com.wherelee.edu.auth.dto.LoginResponse;
import com.wherelee.edu.auth.dto.RegisterRequest;
import com.wherelee.edu.auth.dto.UserMeVO;
import com.wherelee.edu.common.ApiResponse;
import com.wherelee.edu.security.CurrentUser;
import com.wherelee.edu.security.UserContext;
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

    @PostMapping("/register")
    public ApiResponse<LoginResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.ok(authService.register(request));
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(authService.login(request.username(), request.password()));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout() {
        authService.logout();
        return ApiResponse.ok();
    }

    @PutMapping("/password")
    public ApiResponse<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(request.oldPassword(), request.newPassword());
        return ApiResponse.ok();
    }

    @GetMapping("/me")
    public ApiResponse<UserMeVO> me(@CurrentUser UserContext.Principal principal) {
        return ApiResponse.ok(authService.me());
    }
}
