package com.wherelee.edu.user;

import com.wherelee.edu.audit.AuditLog;
import com.wherelee.edu.common.ApiResponse;
import com.wherelee.edu.security.CurrentUser;
import com.wherelee.edu.user.dto.UpdateProfileRequest;
import com.wherelee.edu.user.dto.UserProfileVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 用户自助资料：看自己的资料、改昵称简介、换头像。
 * 这些都是登录用户对自己做的操作，不需要任何权限点。
 */
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserProfileController {

    private final UserProfileService userProfileService;

    @GetMapping("/profile")
    public ApiResponse<UserProfileVO> profile(@CurrentUser Long userId) {
        return ApiResponse.ok(userProfileService.get(userId));
    }

    @PutMapping("/profile")
    @AuditLog(action = "USER_PROFILE_UPDATE", targetType = "USER")
    public ApiResponse<Void> update(@CurrentUser Long userId,
                                    @Valid @RequestBody UpdateProfileRequest request) {
        userProfileService.update(userId, request);
        return ApiResponse.ok();
    }

    @PostMapping("/avatar")
    @AuditLog(action = "USER_AVATAR_UPLOAD", targetType = "USER")
    public ApiResponse<UserProfileVO> uploadAvatar(@CurrentUser Long userId,
                                                   @RequestParam("file") MultipartFile file) {
        return ApiResponse.ok(userProfileService.uploadAvatar(userId, file));
    }
}
