package com.linger.module.groupbuy.inventory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.linger.module.groupbuy.inventory.entity.GroupBuyInventoryReservationEntity;
import org.apache.ibatis.annotations.Param;

public interface GroupBuyInventoryReservationMapper extends BaseMapper<GroupBuyInventoryReservationEntity> {

    GroupBuyInventoryReservationEntity selectByOrderId(@Param("orderId") String orderId);

    int markConfirmed(@Param("orderId") String orderId);

    int markReleased(@Param("orderId") String orderId);

    int markRefunded(@Param("orderId") String orderId,
                     @Param("expectedStatus") String expectedStatus);
}
