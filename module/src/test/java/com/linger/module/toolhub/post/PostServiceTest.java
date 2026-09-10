package com.linger.module.toolhub.post;

import com.linger.module.toolhub.auth.AuthService;
import com.linger.module.toolhub.auth.UserRecord;
import com.linger.module.toolhub.post.dto.PostResponse;
import com.linger.module.toolhub.post.dto.PostUpsertRequest;
import com.linger.module.toolhub.post.entity.PostEntity;
import com.linger.module.toolhub.post.entity.PostTagEntity;
import com.linger.module.toolhub.post.mapper.PostCommentMapper;
import com.linger.module.toolhub.post.mapper.PostMapper;
import com.linger.module.toolhub.post.mapper.PostTagMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostServiceTest {

    @Mock
    private PostMapper postMapper;
    @Mock
    private PostCommentMapper postCommentMapper;
    @Mock
    private PostTagMapper postTagMapper;
    @Mock
    private AuthService authService;
    @InjectMocks
    private PostService postService;

    @Test
    void shouldCreateTypedPostAndNormalizeTags() {
        UserRecord user = new UserRecord();
        user.setUserId("user-1");
        user.setUsername("tester");
        user.setRoles(Collections.emptyList());
        user.setPermissions(Collections.emptyList());
        when(authService.currentUser()).thenReturn(user);
        when(postMapper.insert(any(PostEntity.class))).thenReturn(1);
        when(postTagMapper.insertBatch(anyList())).thenAnswer(invocation ->
                ((List<?>) invocation.getArgument(0)).size());

        PostUpsertRequest request = new PostUpsertRequest();
        request.setTitle("  标题  ");
        request.setContent("  正文  ");
        request.setTags(Arrays.asList(" Java ", "Java", "MyBatis-Plus"));

        PostResponse response = postService.create(request);

        assertNotNull(response.getId());
        assertEquals("标题", response.getTitle());
        assertEquals("正文", response.getContent());
        assertEquals("经验分享", response.getCategory());
        assertEquals(Arrays.asList("Java", "MyBatis-Plus"), response.getTags());

        ArgumentCaptor<List<PostTagEntity>> tagsCaptor = ArgumentCaptor.forClass(List.class);
        verify(postTagMapper).insertBatch(tagsCaptor.capture());
        assertEquals(2, tagsCaptor.getValue().size());
        assertEquals("user-1", tagsCaptor.getValue().get(0).getCreatedBy());
    }
}
