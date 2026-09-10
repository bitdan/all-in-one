package com.linger.module.toolhub.post.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.linger.module.toolhub.post.entity.PostTagEntity;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface PostTagMapper extends BaseMapper<PostTagEntity> {

    int insertBatch(@Param("tags") List<PostTagEntity> tags);
}
