package com.live_commerce.product.product.domain.port.in;

import com.live_commerce.product.product.application.dto.result.ProductResult;

import java.util.UUID;

public interface GetProductUseCase {
    ProductResult getProduct(UUID productId);
}
