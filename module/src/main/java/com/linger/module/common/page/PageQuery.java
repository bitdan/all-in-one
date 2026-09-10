package com.linger.module.common.page;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PageQuery {

    public static final long DEFAULT_PAGE = 1L;
    public static final long DEFAULT_PAGE_SIZE = 20L;
    public static final long MAX_PAGE_SIZE = 100L;

    private long page = DEFAULT_PAGE;
    private long pageSize = DEFAULT_PAGE_SIZE;

    public void setPage(long page) {
        this.page = Math.max(DEFAULT_PAGE, page);
    }

    public void setPageSize(long pageSize) {
        this.pageSize = Math.max(1L, Math.min(pageSize, MAX_PAGE_SIZE));
    }

    /**
     * Compatibility alias for the public API's snake_case query parameter.
     */
    public void setPage_size(long pageSize) {
        setPageSize(pageSize);
    }

    public <T> Page<T> toPage() {
        return toPage(true);
    }

    public <T> Page<T> toPage(boolean searchCount) {
        return new Page<>(page, pageSize, searchCount);
    }
}
