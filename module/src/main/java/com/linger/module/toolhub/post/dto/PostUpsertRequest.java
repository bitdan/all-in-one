package com.linger.module.toolhub.post.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class PostUpsertRequest {

    private String title;
    private String category;
    private List<String> tags = new ArrayList<>();
    private String content;
}
