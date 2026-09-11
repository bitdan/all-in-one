package com.linger.module.groupbuy.transaction.controller;

import com.linger.module.groupbuy.transaction.dto.CreateActivityRequest;
import com.linger.module.groupbuy.transaction.dto.CreateGroupRequest;
import com.linger.module.groupbuy.transaction.dto.GroupBuyApiResponse;
import com.linger.module.groupbuy.transaction.dto.PaymentCallbackRequest;
import com.linger.module.groupbuy.transaction.dto.PlaceGroupOrderRequest;
import com.linger.module.groupbuy.transaction.dto.PlaceGroupOrderResponse;
import com.linger.module.groupbuy.activity.entity.GroupBuyActivityEntity;
import com.linger.module.groupbuy.group.entity.GroupBuyGroupEntity;
import com.linger.module.groupbuy.order.entity.GroupBuyOrderEntity;
import com.linger.module.groupbuy.transaction.service.GroupBuyTransactionApplicationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/groupbuy")
public class GroupBuyTransactionController {

    private final GroupBuyTransactionApplicationService applicationService;

    @PostMapping("/activities")
    public GroupBuyApiResponse<GroupBuyActivityEntity> createActivity(@RequestBody CreateActivityRequest request) {
        return GroupBuyApiResponse.success(applicationService.createActivity(request));
    }

    @PostMapping("/activities/{activityId}/publish")
    public GroupBuyApiResponse<GroupBuyActivityEntity> publishActivity(@PathVariable Long activityId) {
        return GroupBuyApiResponse.success(applicationService.publishActivity(activityId));
    }

    @PostMapping("/activities/{activityId}/groups")
    public GroupBuyApiResponse<GroupBuyGroupEntity> createGroup(@PathVariable Long activityId,
                                                                 @RequestBody CreateGroupRequest request) {
        return GroupBuyApiResponse.success(applicationService.createGroup(activityId, request));
    }

    @PostMapping("/orders")
    public GroupBuyApiResponse<PlaceGroupOrderResponse> placeOrder(@RequestBody PlaceGroupOrderRequest request) {
        PlaceGroupOrderResponse response = applicationService.placeOrder(request);
        boolean accepted = "ACCEPTED".equals(response.getCode()) || "DUPLICATE_REQUEST".equals(response.getCode());
        return new GroupBuyApiResponse<>(accepted, response.getCode(), response.getMessage(), response);
    }

    @GetMapping("/orders/{orderId}")
    public GroupBuyApiResponse<GroupBuyOrderEntity> getOrder(@PathVariable String orderId) {
        return GroupBuyApiResponse.success(applicationService.findOrder(orderId));
    }

    @PostMapping("/payments/callback")
    public GroupBuyApiResponse<String> paymentCallback(@RequestBody PaymentCallbackRequest request) {
        return GroupBuyApiResponse.success(applicationService.recordPayment(request));
    }
}
