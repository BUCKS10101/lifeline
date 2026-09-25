package com.personalos.backend.common.paging;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/** The paged list body defined in docs/api-conventions.md. */
public record PagedResponse<T>(List<T> items, int page, int size, long totalItems, int totalPages) {

    public static <E, T> PagedResponse<T> of(Page<E> page, Function<E, T> mapper) {
        return new PagedResponse<>(page.getContent().stream().map(mapper).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

    public static <T> PagedResponse<T> of(Page<T> page) {
        return of(page, Function.identity());
    }
}
