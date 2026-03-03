package com.live_commerce.product.product.application.dto.result;

import org.springframework.data.domain.Page;

import java.util.List;

public record ProductPageResult(
        List<ProductResult> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    public static ProductPageResult from(Page<ProductResult> page) {
        return new ProductPageResult(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }
}
