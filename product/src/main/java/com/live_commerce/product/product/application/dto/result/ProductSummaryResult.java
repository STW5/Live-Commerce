package com.live_commerce.product.product.application.dto.result;

import com.live_commerce.product.product.domain.model.Product;
import com.live_commerce.product.product.domain.model.ProductCategory;
import com.live_commerce.product.product.domain.model.ProductStatus;

import java.util.UUID;

public record ProductSummaryResult(
        UUID productId,
        String name,
        Integer price,
        ProductCategory category,
        ProductStatus productStatus
) {
    public static ProductSummaryResult from(Product product) {
        return new ProductSummaryResult(
                product.getProductId(),
                product.getName(),
                product.getPrice(),
                product.getCategory(),
                product.getProductStatus()
        );
    }
}
