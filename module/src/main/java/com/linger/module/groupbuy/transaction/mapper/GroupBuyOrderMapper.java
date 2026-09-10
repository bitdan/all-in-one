package com.linger.module.groupbuy.transaction.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.linger.module.groupbuy.transaction.entity.GroupBuyOrderEntity;
import org.apache.ibatis.annotations.Param;

import java.time.OffsetDateTime;
import java.util.List;

public interface GroupBuyOrderMapper extends BaseMapper<GroupBuyOrderEntity> {

    GroupBuyOrderEntity selectByUserRequest(@Param("userId") Long userId,
                                             @Param("requestId") String requestId);

    int markWaitPay(@Param("orderId") String orderId,
                    @Param("reservationId") String reservationId,
                    @Param("payDeadline") OffsetDateTime payDeadline);

    int markRejected(@Param("orderId") String orderId, @Param("reason") String reason);

    int markPaid(@Param("orderId") String orderId,
                 @Param("paymentNo") String paymentNo,
                 @Param("paidAt") OffsetDateTime paidAt);

    int cancelUnpaid(@Param("orderId") String orderId);

    int markGroupSuccess(@Param("groupId") Long groupId);

    int markRefundingByGroup(@Param("groupId") Long groupId);

    int markRefunding(@Param("orderId") String orderId);

    int markRefunded(@Param("orderId") String orderId);

    List<GroupBuyOrderEntity> selectStaleInit(@Param("before") OffsetDateTime before,
                                               @Param("limit") int limit);

    List<GroupBuyOrderEntity> selectExpiredUnpaid(@Param("limit") int limit);

    List<GroupBuyOrderEntity> selectByGroupId(@Param("groupId") Long groupId);
}
