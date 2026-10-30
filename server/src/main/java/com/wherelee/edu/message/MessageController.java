package com.wherelee.edu.message;

import com.wherelee.edu.common.ApiResponse;
import com.wherelee.edu.common.PageResult;
import com.wherelee.edu.message.dto.MessageDtos;
import com.wherelee.edu.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/messages")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;

    @GetMapping
    public ApiResponse<PageResult<MessageDtos.MessageSummaryVO>> inbox(@CurrentUser Long userId,
                                                                      @RequestParam(required = false) Boolean isRead,
                                                                      @RequestParam(defaultValue = "1") int page,
                                                                      @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(messageService.inbox(userId, isRead, page, size));
    }

    @GetMapping("/unread-count")
    public ApiResponse<Long> unreadCount(@CurrentUser Long userId) {
        return ApiResponse.ok(messageService.unreadCount(userId));
    }

    @GetMapping("/{id}")
    public ApiResponse<MessageDtos.MessageVO> detail(@CurrentUser Long userId, @PathVariable Long id) {
        return ApiResponse.ok(messageService.detail(userId, id));
    }

    @PutMapping("/{id}/read")
    public ApiResponse<Void> markRead(@CurrentUser Long userId, @PathVariable Long id) {
        messageService.markRead(userId, id);
        return ApiResponse.ok();
    }

    @PutMapping("/read-all")
    public ApiResponse<Integer> markAllRead(@CurrentUser Long userId) {
        return ApiResponse.ok(messageService.markAllRead(userId));
    }
}
