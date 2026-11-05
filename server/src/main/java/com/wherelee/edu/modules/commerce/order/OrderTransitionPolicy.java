package com.wherelee.edu.modules.commerce.order;

import com.wherelee.edu.modules.platform.common.BizException;

/**
 * 订单状态转换策略。
 *
 * <p>只集中“当前状态是否允许某个操作”的业务规则，不替代数据库条件更新。
 * 真正的状态变更仍由 SQL 的 WHERE status 保证并发安全。</p>
 */
public final class OrderTransitionPolicy {

    private OrderTransitionPolicy() {
    }

    public static void requireCustomerClosable(OrderStatus status) {
        if (status == OrderStatus.PAID || status == OrderStatus.FULFILLED) {
            throw BizException.badRequest("已支付订单不能直接关闭");
        }
        if (status == OrderStatus.PAYING) {
            throw BizException.badRequest("支付中订单不能直接关闭，请先查询支付结果");
        }
    }

    public static void requireAdminClosable(OrderStatus status) {
        if (status == OrderStatus.PAID || status == OrderStatus.FULFILLED
                || status == OrderStatus.REFUNDING || status == OrderStatus.REFUNDED
                || status == OrderStatus.REFUND_FAILED) {
            throw BizException.badRequest("已支付订单不能直接关闭");
        }
        if (status == OrderStatus.PAYING) {
            throw BizException.badRequest("支付中订单不能直接关闭，请先主动查单");
        }
    }

    public static void requireRefundable(OrderStatus status) {
        if (status != OrderStatus.PAID
                && status != OrderStatus.FULFILLED
                && status != OrderStatus.REFUND_FAILED) {
            throw BizException.badRequest("当前订单状态不能退款");
        }
    }

    public static boolean isFulfillable(OrderStatus status) {
        return status == OrderStatus.PAID;
    }
}