package com.linger.module.toolhub.post;

import com.linger.module.common.ApiResponse;
import com.linger.module.common.page.PageResult;
import com.linger.module.toolhub.post.dto.CreateCommentRequest;
import com.linger.module.toolhub.post.dto.PostCommentResponse;
import com.linger.module.toolhub.post.dto.PostListQuery;
import com.linger.module.toolhub.post.dto.PostResponse;
import com.linger.module.toolhub.post.dto.PostUpsertRequest;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/posts")
@AllArgsConstructor
public class PostController {

    private final PostService postService;

    @GetMapping
    public ApiResponse<PageResult<PostResponse>> list(@ModelAttribute PostListQuery query) {
        return ApiResponse.success("获取帖子列表成功", postService.list(query));
    }

    @GetMapping("/{postId}")
    public ApiResponse<PostResponse> get(@PathVariable String postId) {
        return ApiResponse.success("获取帖子成功", postService.get(postId));
    }

    @PostMapping
    public ApiResponse<PostResponse> create(@RequestBody PostUpsertRequest request) {
        return ApiResponse.success("发布成功", postService.create(request));
    }

    @PutMapping("/{postId}")
    public ApiResponse<PostResponse> update(@PathVariable String postId, @RequestBody PostUpsertRequest request) {
        return ApiResponse.success("更新成功", postService.update(postId, request));
    }

    @DeleteMapping("/{postId}")
    public ApiResponse<Void> delete(@PathVariable String postId) {
        postService.delete(postId);
        return ApiResponse.success("删除成功");
    }

    @GetMapping("/{postId}/comments")
    public ApiResponse<List<PostCommentResponse>> comments(@PathVariable String postId) {
        return ApiResponse.success("获取回复成功", postService.comments(postId));
    }

    @PostMapping("/{postId}/comments")
    public ApiResponse<PostCommentResponse> createComment(@PathVariable String postId,
                                                           @RequestBody CreateCommentRequest request) {
        return ApiResponse.success("回复成功", postService.createComment(postId, request));
    }
}
