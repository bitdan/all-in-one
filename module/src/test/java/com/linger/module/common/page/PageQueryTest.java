package com.linger.module.common.page;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.web.bind.WebDataBinder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PageQueryTest {

    @Test
    void shouldBindSnakeCaseAndClampPagination() {
        PageQuery query = new PageQuery();
        MutablePropertyValues values = new MutablePropertyValues();
        values.add("page", 0);
        values.add("page_size", 500);

        new WebDataBinder(query).bind(values);

        assertEquals(1L, query.getPage());
        assertEquals(100L, query.getPageSize());
        Page<Object> page = query.toPage(false);
        assertEquals(1L, page.getCurrent());
        assertEquals(100L, page.getSize());
        assertFalse(page.searchCount());
    }

    @Test
    void shouldSerializePageResultWithSnakeCaseNaming() {
        PageResult<String> result = new PageResult<>(java.util.Collections.singletonList("item"), 1L, 2L, 20L);

        JsonNode json = new ObjectMapper().valueToTree(result);

        assertTrue(json.has("page_size"));
        assertFalse(json.has("pageSize"));
    }
}
