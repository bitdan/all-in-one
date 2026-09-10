package com.linger.module.toolhub.post;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.linger.module.common.page.PageResult;
import com.linger.module.exception.BusinessException;
import com.linger.module.toolhub.auth.AuthService;
import com.linger.module.toolhub.auth.UserRecord;
import com.linger.module.toolhub.post.dto.CreateCommentRequest;
import com.linger.module.toolhub.post.dto.PostCommentResponse;
import com.linger.module.toolhub.post.dto.PostListQuery;
import com.linger.module.toolhub.post.dto.PostResponse;
import com.linger.module.toolhub.post.dto.PostUpsertRequest;
import com.linger.module.toolhub.post.entity.PostCommentEntity;
import com.linger.module.toolhub.post.entity.PostEntity;
import com.linger.module.toolhub.post.entity.PostTagEntity;
import com.linger.module.toolhub.post.mapper.PostCommentMapper;
import com.linger.module.toolhub.post.mapper.PostMapper;
import com.linger.module.toolhub.post.mapper.PostTagMapper;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@AllArgsConstructor
public class PostService {

    private static final String PUBLISHED = "published";
    private static final String DELETED = "deleted";
    private static final String DEFAULT_CATEGORY = "经验分享";
    private static final int MAX_TITLE_LENGTH = 120;
    private static final int MAX_CATEGORY_LENGTH = 64;
    private static final int MAX_TAG_LENGTH = 64;
    private static final int MAX_TAGS = 10;

    private final PostMapper postMapper;
    private final PostCommentMapper postCommentMapper;
    private final PostTagMapper postTagMapper;
    private final AuthService authService;

    public PageResult<PostResponse> list(PostListQuery query) {
        query.setKeyword(trimToNull(query.getKeyword()));
        query.setCategory(trimToNull(query.getCategory()));
        query.setTag(trimToNull(query.getTag()));
        IPage<PostEntity> result = postMapper.selectPostPage(query.toPage(), query);
        Map<String, List<String>> tagsByPostId = tagsByPostId(result.getRecords());
        String currentUserId = authService.optionalUserId();
        boolean admin = isAdmin(currentUserId);
        return PageResult.from(result, post -> toResponse(
                post, tagsByPostId.getOrDefault(post.getId(), Collections.emptyList()), false,
                currentUserId, admin));
    }

    public PostResponse get(String postId) {
        postMapper.incrementViewCount(postId);
        String currentUserId = authService.optionalUserId();
        return toResponse(findPost(postId), tags(postId), true, currentUserId, isAdmin(currentUserId));
    }

    @Transactional(rollbackFor = Exception.class)
    public PostResponse create(PostUpsertRequest request) {
        UserRecord user = authService.currentUser();
        String title = normalizeTitle(request.getTitle());
        String content = required(request.getContent(), "正文不能为空");
        String category = normalizeCategory(request.getCategory());
        List<String> tags = normalizeTags(request.getTags());
        OffsetDateTime now = now();

        PostEntity post = new PostEntity();
        post.setId(UUID.randomUUID().toString());
        post.setTitle(title);
        post.setContent(content);
        post.setCategory(category);
        post.setAuthorId(user.getUserId());
        post.setAuthorName(user.getUsername());
        post.setStatus(PUBLISHED);
        post.setViewCount(0);
        post.setLikeCount(0);
        post.setCommentCount(0);
        post.setCreatedBy(user.getUserId());
        post.setUpdatedBy(user.getUserId());
        post.setCreatedAt(now);
        post.setUpdatedAt(now);
        requireSingleRow(postMapper.insert(post), "发布帖子失败");

        replaceTags(post.getId(), tags, user.getUserId());
        return toResponse(post, tags, true, user.getUserId(), isAdmin(user));
    }

    @Transactional(rollbackFor = Exception.class)
    public PostResponse update(String postId, PostUpsertRequest request) {
        UserRecord current = authService.currentUser();
        PostEntity existing = findPost(postId);
        requireEdit(existing.getAuthorId(), current);

        String title = normalizeTitle(request.getTitle());
        String content = required(request.getContent(), "正文不能为空");
        String category = normalizeCategory(request.getCategory());
        List<String> tags = normalizeTags(request.getTags());
        OffsetDateTime updatedAt = now();

        int rows = postMapper.update(null, new LambdaUpdateWrapper<PostEntity>()
                .eq(PostEntity::getId, postId)
                .eq(PostEntity::getStatus, PUBLISHED)
                .set(PostEntity::getTitle, title)
                .set(PostEntity::getContent, content)
                .set(PostEntity::getCategory, category)
                .set(PostEntity::getUpdatedBy, current.getUserId())
                .set(PostEntity::getUpdatedAt, updatedAt));
        requireSingleRow(rows, "帖子不存在或已被删除");
        replaceTags(postId, tags, current.getUserId());

        existing.setTitle(title);
        existing.setContent(content);
        existing.setCategory(category);
        existing.setUpdatedBy(current.getUserId());
        existing.setUpdatedAt(updatedAt);
        return toResponse(existing, tags, true, current.getUserId(), isAdmin(current));
    }

    public void delete(String postId) {
        UserRecord current = authService.currentUser();
        PostEntity existing = findPost(postId);
        requireEdit(existing.getAuthorId(), current);
        int rows = postMapper.update(null, new LambdaUpdateWrapper<PostEntity>()
                .eq(PostEntity::getId, postId)
                .eq(PostEntity::getStatus, PUBLISHED)
                .set(PostEntity::getStatus, DELETED)
                .set(PostEntity::getUpdatedBy, current.getUserId())
                .set(PostEntity::getUpdatedAt, now()));
        requireSingleRow(rows, "帖子不存在或已被删除");
    }

    public List<PostCommentResponse> comments(String postId) {
        ensurePostExists(postId);
        List<PostCommentEntity> comments = postCommentMapper.selectList(
                Wrappers.lambdaQuery(PostCommentEntity.class)
                        .eq(PostCommentEntity::getPostId, postId)
                        .eq(PostCommentEntity::getStatus, PUBLISHED)
                        .orderByAsc(PostCommentEntity::getCreatedAt)
                        .orderByAsc(PostCommentEntity::getId));
        String currentUserId = authService.optionalUserId();
        boolean admin = isAdmin(currentUserId);
        List<PostCommentResponse> result = new ArrayList<>(comments.size());
        for (PostCommentEntity comment : comments) {
            result.add(toCommentResponse(comment, currentUserId, admin));
        }
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public PostCommentResponse createComment(String postId, CreateCommentRequest request) {
        UserRecord user = authService.currentUser();
        ensurePostExists(postId);
        String content = required(request.getContent(), "回复内容不能为空");
        String parentId = trimToNull(request.getParentId());
        String replyToAuthorName = null;
        if (parentId != null) {
            PostCommentEntity parent = postCommentMapper.selectOne(
                    Wrappers.lambdaQuery(PostCommentEntity.class)
                            .eq(PostCommentEntity::getId, parentId)
                            .eq(PostCommentEntity::getPostId, postId)
                            .eq(PostCommentEntity::getStatus, PUBLISHED));
            if (parent == null) {
                throw BusinessException.badRequest("被回复的评论不存在");
            }
            replyToAuthorName = parent.getAuthorName();
        }

        OffsetDateTime now = now();
        PostCommentEntity comment = new PostCommentEntity();
        comment.setId(UUID.randomUUID().toString());
        comment.setPostId(postId);
        comment.setParentId(parentId);
        comment.setAuthorId(user.getUserId());
        comment.setAuthorName(user.getUsername());
        comment.setReplyToAuthorName(replyToAuthorName);
        comment.setContent(content);
        comment.setStatus(PUBLISHED);
        comment.setCreatedBy(user.getUserId());
        comment.setUpdatedBy(user.getUserId());
        comment.setCreatedAt(now);
        comment.setUpdatedAt(now);
        requireSingleRow(postCommentMapper.insert(comment), "回复失败");
        requireSingleRow(postMapper.incrementCommentCount(postId, user.getUserId()), "帖子不存在或已被删除");
        return toCommentResponse(comment, user.getUserId(), isAdmin(user));
    }

    private PostEntity findPost(String postId) {
        PostEntity post = postMapper.selectOne(Wrappers.lambdaQuery(PostEntity.class)
                .eq(PostEntity::getId, postId)
                .eq(PostEntity::getStatus, PUBLISHED));
        if (post == null) {
            throw BusinessException.notFound("帖子不存在");
        }
        return post;
    }

    private void ensurePostExists(String postId) {
        Long count = postMapper.selectCount(Wrappers.lambdaQuery(PostEntity.class)
                .eq(PostEntity::getId, postId)
                .eq(PostEntity::getStatus, PUBLISHED));
        if (count == null || count == 0L) {
            throw BusinessException.notFound("帖子不存在");
        }
    }

    private Map<String, List<String>> tagsByPostId(List<PostEntity> posts) {
        if (posts.isEmpty()) {
            return Collections.emptyMap();
        }
        List<String> postIds = new ArrayList<>(posts.size());
        for (PostEntity post : posts) {
            postIds.add(post.getId());
        }
        List<PostTagEntity> tags = postTagMapper.selectList(Wrappers.lambdaQuery(PostTagEntity.class)
                .in(PostTagEntity::getPostId, postIds)
                .orderByAsc(PostTagEntity::getId));
        Map<String, List<String>> result = new LinkedHashMap<>();
        for (PostTagEntity tag : tags) {
            result.computeIfAbsent(tag.getPostId(), ignored -> new ArrayList<>()).add(tag.getTagName());
        }
        return result;
    }

    private List<String> tags(String postId) {
        List<PostTagEntity> entities = postTagMapper.selectList(Wrappers.lambdaQuery(PostTagEntity.class)
                .eq(PostTagEntity::getPostId, postId)
                .orderByAsc(PostTagEntity::getId));
        List<String> result = new ArrayList<>(entities.size());
        for (PostTagEntity entity : entities) {
            result.add(entity.getTagName());
        }
        return result;
    }

    private void replaceTags(String postId, List<String> tags, String actorId) {
        postTagMapper.delete(Wrappers.lambdaQuery(PostTagEntity.class)
                .eq(PostTagEntity::getPostId, postId));
        if (tags.isEmpty()) {
            return;
        }
        OffsetDateTime now = now();
        List<PostTagEntity> entities = new ArrayList<>(tags.size());
        for (String tag : tags) {
            PostTagEntity entity = new PostTagEntity();
            entity.setPostId(postId);
            entity.setTagName(tag);
            entity.setCreatedBy(actorId);
            entity.setUpdatedBy(actorId);
            entity.setCreatedAt(now);
            entity.setUpdatedAt(now);
            entities.add(entity);
        }
        int rows = postTagMapper.insertBatch(entities);
        if (rows != entities.size()) {
            throw BusinessException.unavailable("保存帖子标签失败");
        }
    }

    private PostResponse toResponse(PostEntity post, List<String> tags, boolean includeContent,
                                    String currentUserId, boolean admin) {
        return PostResponse.builder()
                .id(post.getId())
                .title(post.getTitle())
                .category(post.getCategory())
                .tags(tags)
                .content(includeContent ? post.getContent() : null)
                .authorId(post.getAuthorId())
                .authorName(post.getAuthorName())
                .viewCount(post.getViewCount())
                .likeCount(post.getLikeCount())
                .commentCount(post.getCommentCount())
                .createdAt(post.getCreatedAt())
                .updatedAt(post.getUpdatedAt())
                .canEdit(canEdit(post.getAuthorId(), currentUserId, admin))
                .build();
    }

    private PostCommentResponse toCommentResponse(PostCommentEntity comment, String currentUserId, boolean admin) {
        return PostCommentResponse.builder()
                .id(comment.getId())
                .postId(comment.getPostId())
                .parentId(comment.getParentId())
                .authorId(comment.getAuthorId())
                .authorName(comment.getAuthorName())
                .replyToAuthorName(comment.getReplyToAuthorName())
                .content(comment.getContent())
                .createdAt(comment.getCreatedAt())
                .updatedAt(comment.getUpdatedAt())
                .canEdit(canEdit(comment.getAuthorId(), currentUserId, admin))
                .build();
    }

    private void requireEdit(String authorId, UserRecord current) {
        if (!canEdit(authorId, current.getUserId(), isAdmin(current))) {
            throw BusinessException.forbidden("无权修改该帖子");
        }
    }

    private boolean canEdit(String authorId, String currentUserId, boolean admin) {
        return currentUserId != null && (currentUserId.equals(authorId) || admin);
    }

    private boolean isAdmin(String userId) {
        return userId != null && isAdmin(authService.getUser(userId));
    }

    private boolean isAdmin(UserRecord user) {
        return user.getRoles().contains("admin") || user.getPermissions().contains("*");
    }

    private List<String> normalizeTags(List<String> values) {
        if (values == null || values.isEmpty()) {
            return Collections.emptyList();
        }
        Set<String> unique = new LinkedHashSet<>();
        for (String value : values) {
            String tag = trimToNull(value);
            if (tag == null) {
                continue;
            }
            if (tag.length() > MAX_TAG_LENGTH) {
                throw BusinessException.badRequest("标签不能超过64个字符");
            }
            unique.add(tag);
            if (unique.size() > MAX_TAGS) {
                throw BusinessException.badRequest("标签不能超过10个");
            }
        }
        return new ArrayList<>(unique);
    }

    private String normalizeCategory(String value) {
        String category = trimToNull(value);
        if (category == null) {
            return DEFAULT_CATEGORY;
        }
        if (category.length() > MAX_CATEGORY_LENGTH) {
            throw BusinessException.badRequest("分类不能超过64个字符");
        }
        return category;
    }

    private String normalizeTitle(String value) {
        String title = required(value, "标题不能为空");
        if (title.length() > MAX_TITLE_LENGTH) {
            throw BusinessException.badRequest("标题不能超过120个字符");
        }
        return title;
    }

    private String required(String value, String message) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            throw BusinessException.badRequest(message);
        }
        return normalized;
    }

    private String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }

    private void requireSingleRow(int rows, String message) {
        if (rows != 1) {
            throw BusinessException.unavailable(message);
        }
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }
}
