package com.live_commerce.product.product.application.dto.result;

import com.live_commerce.product.product.domain.model.Product;
import com.live_commerce.product.product.domain.model.ProductCategory;
import com.live_commerce.product.product.domain.model.ProductStatus;

import java.util.UUID;

public record ProductResult(
        UUID productId,
        UUID companyId,
        String name,
        String description,
        Integer price,
        ProductCategory category,
        ProductStatus productStatus,
        boolean soldOut
) {
    public static ProductResult from(Product product, boolean soldOut) {
        return new ProductResult(
                product.getProductId(),
                product.getCompanyId(),
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                product.getCategory(),
                product.getProductStatus(),
                soldOut
        );
    }
}
