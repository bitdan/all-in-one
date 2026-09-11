package com.linger.module.groupbuy.inventory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.linger.module.groupbuy.inventory.entity.GroupBuyInventoryStockEntity;
import org.apache.ibatis.annotations.Param;

public interface GroupBuyInventoryStockMapper extends BaseMapper<GroupBuyInventoryStockEntity> {

    int reserve(@Param("activityId") Long activityId,
                @Param("skuId") String skuId,
                @Param("quantity") Integer quantity);

    int confirm(@Param("activityId") Long activityId,
                @Param("skuId") String skuId,
                @Param("quantity") Integer quantity);

    int release(@Param("activityId") Long activityId,
                @Param("skuId") String skuId,
                @Param("quantity") Integer quantity);

    int refund(@Param("activityId") Long activityId,
               @Param("skuId") String skuId,
               @Param("quantity") Integer quantity);
}
