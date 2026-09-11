package com.linger.module.groupbuy.transaction;

import com.linger.module.groupbuy.transaction.entity.GroupBuyGroupEntity;
import com.linger.module.groupbuy.transaction.entity.GroupBuyInventoryReservationEntity;
import com.linger.module.groupbuy.transaction.entity.GroupBuyMemberEntity;
import com.linger.module.groupbuy.transaction.entity.GroupBuyOrderEntity;
import com.linger.module.groupbuy.transaction.mapper.GroupBuyActivityMapper;
import com.linger.module.groupbuy.transaction.mapper.GroupBuyDelayTaskMapper;
import com.linger.module.groupbuy.transaction.mapper.GroupBuyGroupMapper;
import com.linger.module.groupbuy.transaction.mapper.GroupBuyInventoryLedgerMapper;
import com.linger.module.groupbuy.transaction.mapper.GroupBuyInventoryReservationMapper;
import com.linger.module.groupbuy.transaction.mapper.GroupBuyInventoryStockMapper;
import com.linger.module.groupbuy.transaction.mapper.GroupBuyMemberMapper;
import com.linger.module.groupbuy.transaction.mapper.GroupBuyOrderMapper;
import com.linger.module.groupbuy.transaction.mapper.GroupBuyOutboxEventMapper;
import com.linger.module.groupbuy.transaction.model.GroupInstanceStatus;
import com.linger.module.groupbuy.transaction.model.GroupOrderStatus;
import com.linger.module.groupbuy.transaction.model.InventoryReservationStatus;
import com.linger.module.groupbuy.transaction.service.GroupBuyTransactionStore;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Slf4j
class GroupBuyTransactionStoreTest {

    private GroupBuyGroupMapper groupMapper;
    private GroupBuyOrderMapper orderMapper;
    private GroupBuyMemberMapper memberMapper;
    private GroupBuyInventoryStockMapper inventoryStockMapper;
    private GroupBuyInventoryReservationMapper inventoryReservationMapper;
    private GroupBuyInventoryLedgerMapper ledgerMapper;
    private GroupBuyOutboxEventMapper outboxMapper;
    private GroupBuyDelayTaskMapper delayTaskMapper;
    private GroupBuyTransactionStore store;

    @BeforeEach
    void setUp() {
        GroupBuyActivityMapper activityMapper = mock(GroupBuyActivityMapper.class);
        groupMapper = mock(GroupBuyGroupMapper.class);
        orderMapper = mock(GroupBuyOrderMapper.class);
        memberMapper = mock(GroupBuyMemberMapper.class);
        inventoryStockMapper = mock(GroupBuyInventoryStockMapper.class);
        inventoryReservationMapper = mock(GroupBuyInventoryReservationMapper.class);
        ledgerMapper = mock(GroupBuyInventoryLedgerMapper.class);
        outboxMapper = mock(GroupBuyOutboxEventMapper.class);
        delayTaskMapper = mock(GroupBuyDelayTaskMapper.class);
        store = new GroupBuyTransactionStore(activityMapper, groupMapper, orderMapper, memberMapper,
                inventoryStockMapper, inventoryReservationMapper, ledgerMapper, outboxMapper, delayTaskMapper);
    }

    @Test
    void shouldPersistReservationMirrorAndTimeoutTaskTogether() {
        GroupBuyOrderEntity order = order("order-1", GroupOrderStatus.INIT);
        OffsetDateTime deadline = OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(5);
        when(orderMapper.markWaitPay("order-1", "order-1", deadline)).thenReturn(1);
        when(inventoryStockMapper.reserve(1L, "SKU-1", 1)).thenReturn(1);
        when(groupMapper.incrementReserved(2L)).thenReturn(1);

        store.confirmReservation(order, deadline);

        ArgumentCaptor<GroupBuyMemberEntity> memberCaptor = ArgumentCaptor.forClass(GroupBuyMemberEntity.class);
        verify(memberMapper).insert(memberCaptor.capture());
        verify(inventoryStockMapper).reserve(1L, "SKU-1", 1);
        verify(inventoryReservationMapper).insert(any(GroupBuyInventoryReservationEntity.class));
        verify(ledgerMapper).insertIgnore(1L, "SKU-1", "order-1", "RESERVE", 1);
        verify(delayTaskMapper).insertIgnore("PAYMENT_TIMEOUT", "order-1", deadline);

        log.info("预占事务落库：order={}, member={}, inventoryOperation=RESERVE, quantity={}, timeoutAt={}",
                order, memberCaptor.getValue(), order.getQuantity(), deadline);
    }

    @Test
    void shouldCompleteGroupWhenLastPaidMemberArrives() {
        GroupBuyOrderEntity order = order("order-2", GroupOrderStatus.PAID);
        GroupBuyGroupEntity group = GroupBuyGroupEntity.builder()
                .id(2L)
                .status(GroupInstanceStatus.OPEN)
                .targetCount(3)
                .paidCount(2)
                .reservedCount(3)
                .build();
        GroupBuyGroupEntity updated = GroupBuyGroupEntity.builder()
                .id(2L)
                .status(GroupInstanceStatus.OPEN)
                .targetCount(3)
                .paidCount(3)
                .reservedCount(3)
                .build();
        when(orderMapper.selectById("order-2")).thenReturn(order);
        when(groupMapper.selectForUpdate(2L)).thenReturn(group);
        when(memberMapper.markPaid("order-2")).thenReturn(1);
        when(inventoryReservationMapper.markConfirmed("order-2")).thenReturn(1);
        when(inventoryStockMapper.confirm(1L, "SKU-1", 1)).thenReturn(1);
        when(groupMapper.incrementPaid(2L)).thenReturn(1);
        when(groupMapper.selectById(2L)).thenReturn(updated);
        when(groupMapper.markSuccess(2L)).thenReturn(1);

        GroupBuyTransactionStore.SettlementResult result = store.applyPaidOrder("order-2");

        log.info("末位成员支付成团：beforeGroup={}, afterIncrementGroup={}, settlementResult={}",
                group, updated, result);

        assertTrue(result.isGroupSuccess());
        verify(memberMapper).markConfirmedByGroup(2L);
        verify(orderMapper).markGroupSuccess(2L);
        verify(outboxMapper).insertEvent(anyString(), eq("GROUP_SUCCESS"), eq("GROUP"), eq("2"), eq("{}"));
    }

    @Test
    void shouldReleaseDatabaseSeatOnlyOnceWhenPaymentTimesOut() {
        GroupBuyOrderEntity order = order("order-3", GroupOrderStatus.WAIT_PAY);
        when(orderMapper.selectById("order-3")).thenReturn(order);
        when(orderMapper.cancelUnpaid("order-3")).thenReturn(1);
        when(inventoryReservationMapper.markReleased("order-3")).thenReturn(1);
        when(inventoryStockMapper.release(1L, "SKU-1", 1)).thenReturn(1);

        boolean cancelled = store.cancelUnpaidOrder("order-3");

        log.info("支付超时释放名额：order={}, cancelled={}, releasedGroupId={}",
                order, cancelled, order.getGroupId());

        assertTrue(cancelled);

        verify(groupMapper).decrementReserved(2L);
        verify(memberMapper).cancelReservation("order-3");
        verify(inventoryStockMapper).release(1L, "SKU-1", 1);
        verify(outboxMapper).insertEvent(anyString(), eq("ORDER_RELEASE"), eq("ORDER"), eq("order-3"), eq("{}"));
    }

    @Test
    void shouldNotDecrementPaidCountWhenRefundingBeforePaymentSettlement() {
        GroupBuyOrderEntity order = order("order-4", GroupOrderStatus.REFUNDING);
        GroupBuyInventoryReservationEntity reservation = GroupBuyInventoryReservationEntity.builder()
                .id("order-4")
                .orderId("order-4")
                .status(InventoryReservationStatus.RESERVED)
                .quantity(1)
                .build();
        when(orderMapper.markRefunded("order-4")).thenReturn(1);
        when(inventoryReservationMapper.selectByOrderId("order-4")).thenReturn(reservation);
        when(inventoryReservationMapper.markRefunded("order-4", "RESERVED")).thenReturn(1);
        when(inventoryStockMapper.release(1L, "SKU-1", 1)).thenReturn(1);

        assertTrue(store.markRefunded(order));

        verify(groupMapper).decrementReserved(2L);
        verify(groupMapper, never()).decrementPaidAndReserved(2L);
        verify(inventoryStockMapper).release(1L, "SKU-1", 1);
    }

    private GroupBuyOrderEntity order(String id, GroupOrderStatus status) {
        return GroupBuyOrderEntity.builder()
                .id(id)
                .userId(100L)
                .activityId(1L)
                .groupId(2L)
                .skuId("SKU-1")
                .quantity(1)
                .status(status)
                .build();
    }
}
