package com.wherelee.trade.point.dto;

import com.wherelee.trade.point.PointFlow;

import java.time.LocalDateTime;
import java.util.List;

public record PointSummaryVO(
        Long balance,
        Long frozen,
        List<FlowItem> flows
) {

    public record FlowItem(
            Long id,
            String type,
            Long amount,
            Long balanceAfter,
            LocalDateTime createdAt
    ) {

        public static FlowItem from(PointFlow flow) {
            return new FlowItem(
                    flow.getId(),
                    flow.getType(),
                    flow.getAmount(),
                    flow.getBalanceAfter(),
                    flow.getCreatedAt());
        }
    }
}
