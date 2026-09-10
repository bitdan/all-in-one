package com.linger.module.toolhub.post.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.linger.module.toolhub.post.dto.PostListQuery;
import com.linger.module.toolhub.post.entity.PostEntity;
import org.apache.ibatis.annotations.Param;

public interface PostMapper extends BaseMapper<PostEntity> {

    IPage<PostEntity> selectPostPage(IPage<PostEntity> page, @Param("query") PostListQuery query);

    int incrementViewCount(@Param("postId") String postId);

    int incrementCommentCount(@Param("postId") String postId, @Param("actorId") String actorId);
}
