package com.linger.module.groupbuy.transaction.service;

import com.linger.module.groupbuy.transaction.entity.GroupBuyOrderEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class LocalPaymentGateway implements PaymentGateway {

    @Override
    public boolean refund(GroupBuyOrderEntity order) {
        // 演示实现：真实环境应替换为支付渠道退款，并以 paymentNo 作为渠道幂等键。
        log.info("模拟退款成功, orderId={}, paymentNo={}, amount={}",
                order.getId(), order.getPaymentNo(), order.getPayableAmount());
        return true;
    }
}
