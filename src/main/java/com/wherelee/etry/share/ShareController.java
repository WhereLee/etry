package com.wherelee.etry.share;

import com.wherelee.etry.common.ApiResponse;
import com.wherelee.etry.share.dto.ShareDtos;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/shares")
@RequiredArgsConstructor
public class ShareController {

    private final ShareService shareService;

    @PostMapping
    public ApiResponse<ShareService.ShareView> create(@AuthenticationPrincipal Long userId,
                                                      @Valid @RequestBody ShareDtos.CreateShareRequest request) {
        return ApiResponse.ok(shareService.create(userId, request.fileId(), request.expireDays(), request.code()));
    }

    @GetMapping
    public ApiResponse<List<ShareService.ShareView>> list(@AuthenticationPrincipal Long userId) {
        return ApiResponse.ok(shareService.list(userId));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> revoke(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        shareService.revoke(userId, id);
        return ApiResponse.ok();
    }
}
