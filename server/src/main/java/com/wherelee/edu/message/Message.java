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
    private Boolean isRead;
    private LocalDateTime readAt;
    private LocalDateTime createdAt;
}
