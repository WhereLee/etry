package com.wherelee.etry.user;

import com.wherelee.etry.auth.TokenCookieSupport;
import com.wherelee.etry.common.ApiResponse;
import com.wherelee.etry.user.dto.AvatarUploadResponse;
import com.wherelee.etry.user.dto.UserDtos;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final TokenCookieSupport cookieSupport;

    @PatchMapping
    public ApiResponse<UserView> updateProfile(@AuthenticationPrincipal Long userId,
                                               @Valid @RequestBody UserDtos.UpdateProfileRequest request) {
        User user = userService.updateProfile(userId, request.nickname(), request.bio());
        return ApiResponse.ok(userService.view(user));
    }

    /**
     * 改密会自增 token_version，旧 cookie 立刻失效，
     * 所以这里要用新的版本号重新签一个，避免用户自己被踢下线。
     */
    @PostMapping("/password")
    public ApiResponse<Void> changePassword(@AuthenticationPrincipal Long userId,
                                            @Valid @RequestBody UserDtos.ChangePasswordRequest request,
                                            HttpServletResponse response) {
        User user = userService.changePassword(userId, request.oldPassword(), request.newPassword());
        cookieSupport.write(response, user);
        return ApiResponse.ok();
    }

    @PostMapping("/avatar/presign")
    public ApiResponse<AvatarUploadResponse> presignAvatar(@AuthenticationPrincipal Long userId,
                                                           @Valid @RequestBody UserDtos.AvatarPresignRequest request) {
        AvatarPresign presign = userService.presignAvatar(userId, request.sizeBytes(), request.contentType());
        return ApiResponse.ok(new AvatarUploadResponse(
                presign.objectKey(),
                presign.ticket().url(),
                presign.ticket().method(),
                presign.ticket().formFields(),
                presign.ticket().headers(),
                presign.ticket().expireAt()));
    }

    @PostMapping("/avatar/commit")
    public ApiResponse<UserView> commitAvatar(@AuthenticationPrincipal Long userId,
                                              @Valid @RequestBody UserDtos.AvatarCommitRequest request) {
        User user = userService.commitAvatar(userId, request.objectKey());
        return ApiResponse.ok(userService.view(user));
    }

    @DeleteMapping("/avatar")
    public ApiResponse<UserView> deleteAvatar(@AuthenticationPrincipal Long userId) {
        User user = userService.deleteAvatar(userId);
        return ApiResponse.ok(userService.view(user));
    }
}
