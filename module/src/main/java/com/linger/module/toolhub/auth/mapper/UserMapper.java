package com.linger.module.toolhub.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.linger.module.toolhub.auth.UserRecord;
import com.linger.module.toolhub.auth.dto.AdminUserListQuery;
import com.linger.module.toolhub.auth.dto.UserStatusSummary;
import org.apache.ibatis.annotations.Param;

public interface UserMapper extends BaseMapper<UserRecord> {

    IPage<UserRecord> selectUserPage(IPage<UserRecord> page,
                                     @Param("query") AdminUserListQuery query);

    UserStatusSummary selectStatusSummary(@Param("keyword") String keyword);
}
