package com.linger.module.groupbuy.transaction.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.linger.module.groupbuy.transaction.entity.GroupBuyInventoryLedgerEntity;
import org.apache.ibatis.annotations.Param;

public interface GroupBuyInventoryLedgerMapper extends BaseMapper<GroupBuyInventoryLedgerEntity> {

    int insertIgnore(@Param("activityId") Long activityId,
                     @Param("skuId") String skuId,
                     @Param("orderId") String orderId,
                     @Param("operation") String operation,
                     @Param("quantity") Integer quantity);
}
