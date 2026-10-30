package com.wherelee.etry.share;

import com.wherelee.etry.common.ApiResponse;
import com.wherelee.etry.share.dto.ShareDtos;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * 分享落地页与取件动作。页面本身不需要登录，下载地址由服务端现签。
 */
@Controller
@RequiredArgsConstructor
public class SharePageController {

    private final ShareService shareService;

    @GetMapping("/s/{token}")
    public String page(@PathVariable String token, Model model) {
        ShareService.ShareAccess access = shareService.resolve(token);
        model.addAttribute("token", token);
        if (!access.valid()) {
            model.addAttribute("invalid", true);
            model.addAttribute("message", access.message());
            return "share";
        }
        model.addAttribute("invalid", false);
        model.addAttribute("fileName", access.file().getOriginalName());
        model.addAttribute("sizeBytes", access.file().getSizeBytes());
        model.addAttribute("needCode", access.share().getCode() != null);
        model.addAttribute("expiresAt", access.share().getExpiresAt());
        return "share";
    }

    @PostMapping("/s/{token}/unlock")
    @ResponseBody
    public ApiResponse<ShareDtos.UnlockResponse> unlock(@PathVariable String token,
                                                        @RequestBody ShareDtos.UnlockRequest request,
                                                        HttpServletRequest httpRequest) {
        String url = shareService.unlock(token, request.code(), clientIp(httpRequest));
        return ApiResponse.ok(new ShareDtos.UnlockResponse(url));
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
