package com.linger.module.groupbuy.group.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.linger.module.groupbuy.group.entity.GroupBuyGroupEntity;
import org.apache.ibatis.annotations.Param;

public interface GroupBuyGroupMapper extends BaseMapper<GroupBuyGroupEntity> {

    GroupBuyGroupEntity selectForUpdate(@Param("id") Long id);

    int markOpen(@Param("id") Long id);

    int incrementReserved(@Param("id") Long id);

    int decrementReserved(@Param("id") Long id);

    int incrementPaid(@Param("id") Long id);

    int decrementPaidAndReserved(@Param("id") Long id);

    int markSuccess(@Param("id") Long id);

    int markFailedIfExpired(@Param("id") Long id);
}
