package com.linger.module.groupbuy.transaction.dto;

import com.linger.module.groupbuy.order.model.GroupOrderStatus;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Data
@AllArgsConstructor
public class PlaceGroupOrderResponse {
    private String code;
    private String message;
    private String orderId;
    private GroupOrderStatus status;
    private BigDecimal payableAmount;
    private OffsetDateTime payDeadline;
}

