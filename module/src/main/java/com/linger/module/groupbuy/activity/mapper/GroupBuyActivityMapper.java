package com.linger.module.groupbuy.activity.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.linger.module.groupbuy.activity.entity.GroupBuyActivityEntity;
import org.apache.ibatis.annotations.Param;

public interface GroupBuyActivityMapper extends BaseMapper<GroupBuyActivityEntity> {

    GroupBuyActivityEntity selectForUpdate(@Param("id") Long id);

    int markRunning(@Param("id") Long id);
}
