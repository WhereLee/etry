package com.wherelee.edu.message;

import com.wherelee.edu.audit.AuditLog;
import com.wherelee.edu.common.ApiResponse;
import com.wherelee.edu.message.dto.MessageDtos;
import com.wherelee.edu.rbac.RequirePermission;
import com.wherelee.edu.security.CurrentUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/messages")
@RequiredArgsConstructor
public class MessageAdminController {

    private final MessageService messageService;

    /** 返回实际发出的条数：按角色群发时等于该角色的人数 */
    @PostMapping
    @RequirePermission("message:send")
    @AuditLog(action = "MESSAGE_SEND", targetType = "MESSAGE")
    public ApiResponse<Integer> send(@CurrentUser Long senderId,
                                     @Valid @RequestBody MessageDtos.SendRequest request) {
        return ApiResponse.ok(messageService.send(senderId, request));
    }
}
