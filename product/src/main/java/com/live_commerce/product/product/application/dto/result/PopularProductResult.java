package com.live_commerce.product.product.application.dto.result;

import com.live_commerce.product.product.domain.model.Product;
import com.live_commerce.product.product.domain.model.ProductCategory;

import java.util.UUID;

public record PopularProductResult(
        UUID productId,
        String name,
        Integer price,
        String description,
        ProductCategory category,
        long soldCount
) {
    public static PopularProductResult from(Product product, Long soldCount) {
        return new PopularProductResult(
                product.getProductId(),
                product.getName(),
                product.getPrice(),
                product.getDescription(),
                product.getCategory(),
                soldCount != null ? soldCount : 0L
        );
    }
}
