package com.wherelee.material.modules.course.fulfillment;

import com.wherelee.material.modules.commerce.api.FulfillmentHandler;
import com.wherelee.material.modules.entitlement.api.EntitlementCommand;
import com.wherelee.material.modules.entitlement.api.ProductTypes;
import org.springframework.stereotype.Component;

/**
 * 课程系列履约适配器：把交易核心的发放/撤销事实翻译为权益中心的 ORDER 来源 grant/revoke。
 */
@Component
public class CourseFulfillmentHandler implements FulfillmentHandler {

    private final EntitlementCommand entitlements;

    public CourseFulfillmentHandler(EntitlementCommand entitlements) {
        this.entitlements = entitlements;
    }

    @Override
    public String productType() {
        return ProductTypes.COURSE_SERIES;
    }

    @Override
    public void fulfill(FulfillCommand command) {
        entitlements.grant(new EntitlementCommand.GrantCommand(
                command.userId(), command.productId(), ProductTypes.COURSE_SERIES,
                command.orderId(), null, null, 1));
    }

    @Override
    public void revoke(FulfillCommand command) {
        entitlements.revoke(new EntitlementCommand.RevokeCommand(
                command.userId(), command.productId(), ProductTypes.COURSE_SERIES,
                command.orderId(), command.reason()));
    }
}