package com.myworld.core.helper;

import com.myworld.core.dto.PageResponse;
import org.springframework.data.domain.Page;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class PageHelper {
    private PageHelper() {}

    public static <S, T> PageResponse<T> map(Page<S> page, Function<S, T> mapper) {
        List<T> content = page.getContent().stream().map(mapper).collect(Collectors.toList());
        return PageResponse.<T>builder()
                .content(content)
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .last(page.isLast())
                .build();
    }
}
