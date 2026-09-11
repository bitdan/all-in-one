package com.linger.module.coupon.service;

import com.linger.module.coupon.handler.CouponHandler;
import com.linger.module.coupon.model.Coupon;
import com.linger.module.coupon.model.CouponContext;
import com.linger.module.coupon.model.Order;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * @version 1.0
 * @description CouponChainService
 * @date 2025/7/24 16:57:24
 */
@Service
public class CouponChainService {
    private final CouponHandler firstHandler;

    public CouponChainService(List<CouponHandler> handlers) {
        if (handlers == null || handlers.isEmpty()) {
            throw new IllegalArgumentException("Coupon handlers must not be empty");
        }

        // Spring 注入的集合可能被其他组件共享，不应原地修改。
        List<CouponHandler> orderedHandlers = new ArrayList<>(handlers);
        orderedHandlers.sort(AnnotationAwareOrderComparator.INSTANCE);

        for (int i = 0; i < orderedHandlers.size() - 1; i++) {
            orderedHandlers.get(i).setNext(orderedHandlers.get(i + 1));
        }

        this.firstHandler = orderedHandlers.get(0);
    }

    public Order process(Order order) {
        Objects.requireNonNull(order, "order must not be null");
        // 只保留每种类型的第一张券
        List<Coupon> filtered = order.getCoupons().stream()
                .collect(Collectors.toMap(Coupon::getType, coupon -> coupon, (first, ignored) -> first))
                .values().stream()
                .collect(Collectors.toList());
        order.setCoupons(filtered);
        CouponContext context = new CouponContext();
        context.setOrder(order);
        context.setCurrentPrice(order.getOriginalPrice());

        firstHandler.apply(context);

        order.setFinalPrice(context.getCurrentPrice());
        return order;
    }
}
