package com.linger.module.groupbuy.transaction.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.linger.module.groupbuy.transaction.entity.GroupBuyOutboxEventEntity;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface GroupBuyOutboxEventMapper extends BaseMapper<GroupBuyOutboxEventEntity> {

    int insertEvent(@Param("id") String id,
                    @Param("eventType") String eventType,
                    @Param("aggregateType") String aggregateType,
                    @Param("aggregateId") String aggregateId,
                    @Param("payload") String payload);

    List<GroupBuyOutboxEventEntity> claimBatch(@Param("workerId") String workerId,
                                                @Param("limit") int limit);

    int markDone(@Param("id") String id, @Param("workerId") String workerId);

    int markFailed(@Param("id") String id,
                   @Param("workerId") String workerId,
                   @Param("maxRetries") int maxRetries,
                   @Param("delaySeconds") long delaySeconds,
                   @Param("error") String error);
}
