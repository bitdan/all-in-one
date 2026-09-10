package com.linger.module.common.page;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

@Data
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class PageResult<T> {

    private List<T> items;
    private Long total;
    private Long page;
    private Long pageSize;

    public static <S, T> PageResult<T> from(IPage<S> source, Function<S, T> converter) {
        List<T> items = new ArrayList<>(source.getRecords().size());
        for (S record : source.getRecords()) {
            items.add(converter.apply(record));
        }
        return new PageResult<>(items, source.getTotal(), source.getCurrent(), source.getSize());
    }
}
