package com.wherelee.edu.message.dto;

import com.wherelee.edu.message.Message;

import java.time.LocalDateTime;

public final class MessageDtos {

    public record MessageVO(
            Long id,
            Long senderId,
            String title,
            String content,
            Boolean isRead,
            LocalDateTime readAt,
            LocalDateTime createdAt
    ) {
        public static MessageVO from(Message m) {
            return new MessageVO(m.getId(), m.getSenderId(), m.getTitle(), m.getContent(),
                    m.getIsRead(), m.getReadAt(), m.getCreatedAt());
        }
    }

    /** 列表不返回正文，点进详情再取，省带宽 */
    public record MessageSummaryVO(
            Long id,
            Long senderId,
            String title,
            Boolean isRead,
            LocalDateTime createdAt
    ) {
        public static MessageSummaryVO from(Message m) {
            return new MessageSummaryVO(m.getId(), m.getSenderId(), m.getTitle(),
                    m.getIsRead(), m.getCreatedAt());
        }
    }

    /**
     * 发送请求：要么指定具体收件人，要么按角色群发，二选一。
     */
    public record SendRequest(
            @jakarta.validation.constraints.NotBlank(message = "标题不能为空")
            @jakarta.validation.constraints.Size(max = 100, message = "标题不能超过 100 字")
            String title,

            @jakarta.validation.constraints.NotBlank(message = "正文不能为空")
            String content,

            java.util.List<Long> receiverIds,

            String roleCode
    ) {
    }
}
