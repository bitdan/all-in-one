package com.linger.module.groupbuy.infrastructure.service;

import com.linger.module.groupbuy.order.entity.GroupBuyOrderEntity;

public interface PaymentGateway {
    boolean refund(GroupBuyOrderEntity order);
}

