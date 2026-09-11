package com.linger.module.groupbuy.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.linger.module.groupbuy.infrastructure.entity.GroupBuyDelayTaskEntity;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface GroupBuyDelayTaskMapper extends BaseMapper<GroupBuyDelayTaskEntity> {

    List<GroupBuyDelayTaskEntity> claimBatch(@Param("workerId") String workerId,
                                              @Param("limit") int limit,
                                              @Param("loadAheadSeconds") long loadAheadSeconds,
                                              @Param("lockSeconds") long lockSeconds);

    int insertIgnore(@Param("taskType") String taskType,
                     @Param("businessId") String businessId,
                     @Param("executeAt") java.time.OffsetDateTime executeAt);

    int markRunning(@Param("id") Long id, @Param("workerId") String workerId);

    int markDone(@Param("id") Long id, @Param("workerId") String workerId);

    int markFailed(@Param("id") Long id,
                   @Param("workerId") String workerId,
                   @Param("maxRetries") int maxRetries,
                   @Param("delaySeconds") long delaySeconds,
                   @Param("error") String error);
}
