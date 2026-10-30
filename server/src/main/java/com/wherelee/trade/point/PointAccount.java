package com.wherelee.trade.point;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class PointAccount {

    private Long id;
    private Long userId;
    private Long balance;
    private Long frozen;
    private LocalDateTime updatedAt;
}
