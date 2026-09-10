package com.linger.module.groupbuy.transaction.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.linger.module.groupbuy.transaction.entity.GroupBuyMemberEntity;
import org.apache.ibatis.annotations.Param;

public interface GroupBuyMemberMapper extends BaseMapper<GroupBuyMemberEntity> {

    int markPaid(@Param("orderId") String orderId);

    int markConfirmedByGroup(@Param("groupId") Long groupId);

    int cancelReservation(@Param("orderId") String orderId);

    int markRefundingByGroup(@Param("groupId") Long groupId);

    int markRefunding(@Param("orderId") String orderId);

    int markRefunded(@Param("orderId") String orderId);
}
