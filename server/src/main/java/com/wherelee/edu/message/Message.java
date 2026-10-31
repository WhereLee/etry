package com.wherelee.edu.message;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class Message {

    private Long id;
    private Long receiverId;
    private Long senderId;
    private String title;
    private String content;
    /** 点击后跳转的前端路径。系统通知用，手动群发为空 */
    private String link;
    private Boolean isRead;
    private LocalDateTime readAt;
    private LocalDateTime createdAt;
}
