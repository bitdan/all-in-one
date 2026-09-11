package com.linger.module.groupbuy.transaction;

import com.linger.module.groupbuy.transaction.dto.PaymentCallbackRequest;
import com.linger.module.groupbuy.transaction.dto.PlaceGroupOrderRequest;
import com.linger.module.groupbuy.transaction.dto.PlaceGroupOrderResponse;
import com.linger.module.groupbuy.activity.entity.GroupBuyActivityEntity;
import com.linger.module.groupbuy.group.entity.GroupBuyGroupEntity;
import com.linger.module.groupbuy.order.entity.GroupBuyOrderEntity;
import com.linger.module.groupbuy.transaction.exception.GroupBuyBusinessException;
import com.linger.module.groupbuy.activity.model.ActivityStatus;
import com.linger.module.groupbuy.group.model.GroupInstanceStatus;
import com.linger.module.groupbuy.order.model.GroupOrderStatus;
import com.linger.module.groupbuy.transaction.model.ReservationResult;
import com.linger.module.groupbuy.transaction.service.GroupBuyTransactionApplicationService;
import com.linger.module.groupbuy.transaction.service.GroupBuyTransactionStore;
import com.linger.module.groupbuy.infrastructure.service.RedisGroupBuyAdmissionService;
import com.linger.module.redisson.service.RateLimiterService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Slf4j
class GroupBuyTransactionApplicationServiceTest {

    private GroupBuyTransactionStore store;
    private RedisGroupBuyAdmissionService admissionService;
    private RateLimiterService rateLimiterService;
    private GroupBuyTransactionApplicationService service;
    private GroupBuyActivityEntity activity;
    private GroupBuyGroupEntity group;

    @BeforeEach
    void setUp() {
        store = mock(GroupBuyTransactionStore.class);
        admissionService = mock(RedisGroupBuyAdmissionService.class);
        rateLimiterService = mock(RateLimiterService.class);
        service = new GroupBuyTransactionApplicationService(store, admissionService, rateLimiterService);

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        activity = GroupBuyActivityEntity.builder()
                .id(1L)
                .name("并发拼团")
                .skuId("SKU-1")
                .unitPrice(new BigDecimal("99.90"))
                .totalStock(100)
                .perUserLimit(1)
                .targetCount(3)
                .payTimeoutSeconds(300L)
                .groupTimeoutSeconds(3600L)
                .startsAt(now.minusMinutes(10))
                .endsAt(now.plusHours(1))
                .status(ActivityStatus.RUNNING)
                .build();
        group = GroupBuyGroupEntity.builder()
                .id(2L)
                .activityId(1L)
                .creatorUserId(10L)
                .targetCount(3)
                .reservedCount(0)
                .paidCount(0)
                .status(GroupInstanceStatus.OPEN)
                .expireAt(now.plusMinutes(30))
                .build();

        when(rateLimiterService.tryAcquire(anyString(), anyLong(), anyLong(), any()))
                .thenReturn(true);
        when(store.findActivity(1L)).thenReturn(activity);
        when(store.findGroup(2L)).thenReturn(group);
    }

    @Test
    void shouldCreateWaitPayOrderAfterRedisReservation() {
        PlaceGroupOrderRequest request = orderRequest("request-1");
        AtomicReference<GroupBuyOrderEntity> capturedOrder = new AtomicReference<>();
        doAnswer(invocation -> {
            capturedOrder.set(invocation.getArgument(0));
            return null;
        }).when(store).createInitOrder(any(GroupBuyOrderEntity.class));
        when(admissionService.reserve(eq(activity), eq(group), any(GroupBuyOrderEntity.class), any()))
                .thenAnswer(invocation -> new ReservationResult(
                        ReservationResult.Code.SUCCESS,
                        ((GroupBuyOrderEntity) invocation.getArgument(2)).getId()));
        when(store.findOrder(anyString())).thenAnswer(invocation -> {
            GroupBuyOrderEntity order = capturedOrder.get();
            order.setStatus(GroupOrderStatus.WAIT_PAY);
            order.setPayDeadline(OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(5));
            return order;
        });

        PlaceGroupOrderResponse response = service.placeOrder(request);

        log.info("Redis预占成功下单：requestId={}, initOrder={}, response={}",
                request.getRequestId(), capturedOrder.get(), response);

        assertEquals("ACCEPTED", response.getCode());
        assertEquals(GroupOrderStatus.WAIT_PAY, response.getStatus());
        assertEquals(new BigDecimal("99.90"), response.getPayableAmount());
        verify(store).confirmReservation(any(GroupBuyOrderEntity.class), any());
    }

    @Test
    void shouldPersistRejectedOrderWhenStockIsEmpty() {
        PlaceGroupOrderRequest request = orderRequest("request-2");
        AtomicReference<GroupBuyOrderEntity> capturedOrder = new AtomicReference<>();
        doAnswer(invocation -> {
            capturedOrder.set(invocation.getArgument(0));
            return null;
        }).when(store).createInitOrder(any(GroupBuyOrderEntity.class));
        when(admissionService.reserve(eq(activity), eq(group), any(GroupBuyOrderEntity.class), any()))
                .thenReturn(new ReservationResult(ReservationResult.Code.OUT_OF_STOCK, null));
        when(store.findOrder(anyString())).thenAnswer(invocation -> {
            GroupBuyOrderEntity order = capturedOrder.get();
            order.setStatus(GroupOrderStatus.REJECTED);
            return order;
        });

        PlaceGroupOrderResponse response = service.placeOrder(request);

        log.info("库存不足拒单：requestId={}, rejectedOrder={}, response={}",
                request.getRequestId(), capturedOrder.get(), response);

        assertEquals("OUT_OF_STOCK", response.getCode());
        assertEquals(GroupOrderStatus.REJECTED, response.getStatus());
        verify(store).rejectInitOrder(anyString(), eq("OUT_OF_STOCK"));
        verify(store, never()).confirmReservation(any(), any());
    }

    @Test
    void shouldReturnExistingOrderForDuplicateRequest() {
        GroupBuyOrderEntity existing = GroupBuyOrderEntity.builder()
                .id("existing-order")
                .requestId("request-3")
                .userId(100L)
                .payableAmount(new BigDecimal("99.90"))
                .status(GroupOrderStatus.WAIT_PAY)
                .build();
        when(store.findByUserRequest(100L, "request-3")).thenReturn(existing);

        PlaceGroupOrderResponse response = service.placeOrder(orderRequest("request-3"));

        log.info("幂等请求命中已有订单：existingOrder={}, response={}", existing, response);

        assertEquals("DUPLICATE_REQUEST", response.getCode());
        assertEquals("existing-order", response.getOrderId());
        verify(admissionService, never()).reserve(any(), any(), any(), any());
    }

    @Test
    void shouldRejectMismatchedPaymentAmount() {
        GroupBuyOrderEntity order = GroupBuyOrderEntity.builder()
                .id("order-1")
                .payableAmount(new BigDecimal("99.90"))
                .status(GroupOrderStatus.WAIT_PAY)
                .build();
        when(store.findOrder("order-1")).thenReturn(order);
        PaymentCallbackRequest request = new PaymentCallbackRequest();
        request.setOrderId("order-1");
        request.setPaymentNo("payment-1");
        request.setPaidAmount(new BigDecimal("9.90"));

        GroupBuyBusinessException exception = assertThrows(
                GroupBuyBusinessException.class, () -> service.recordPayment(request));

        log.info("支付金额校验拒绝：orderId={}, payableAmount={}, paidAmount={}, errorCode={}, errorMessage={}",
                order.getId(), order.getPayableAmount(), request.getPaidAmount(),
                exception.getCode(), exception.getMessage());

        assertEquals("PAYMENT_AMOUNT_MISMATCH", exception.getCode());
        assertTrue(exception.getMessage().contains("支付金额"));
        verify(store, never()).recordPayment(anyString(), anyString(), any());
    }

    @Test
    void shouldReturnBusinessConflictWhenPaymentNumberBelongsToAnotherOrder() {
        GroupBuyOrderEntity order = GroupBuyOrderEntity.builder()
                .id("order-2")
                .payableAmount(new BigDecimal("99.90"))
                .status(GroupOrderStatus.WAIT_PAY)
                .build();
        when(store.findOrder("order-2")).thenReturn(order);
        when(store.recordPayment(eq("order-2"), eq("payment-used"), any()))
                .thenThrow(new DuplicateKeyException("duplicate payment_no"));
        PaymentCallbackRequest request = new PaymentCallbackRequest();
        request.setOrderId("order-2");
        request.setPaymentNo("payment-used");
        request.setPaidAmount(new BigDecimal("99.90"));

        GroupBuyBusinessException exception = assertThrows(
                GroupBuyBusinessException.class, () -> service.recordPayment(request));

        assertEquals("PAYMENT_NO_CONFLICT", exception.getCode());
        assertTrue(exception.getMessage().contains("其他订单"));
    }

    private PlaceGroupOrderRequest orderRequest(String requestId) {
        PlaceGroupOrderRequest request = new PlaceGroupOrderRequest();
        request.setRequestId(requestId);
        request.setUserId(100L);
        request.setActivityId(1L);
        request.setGroupId(2L);
        request.setQuantity(1);
        return request;
    }
}
