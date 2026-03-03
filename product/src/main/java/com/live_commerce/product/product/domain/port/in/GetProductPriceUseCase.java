package com.live_commerce.product.product.domain.port.in;

import com.live_commerce.product.product.application.dto.result.ProductPriceResult;

import java.util.UUID;

public interface GetProductPriceUseCase {
    ProductPriceResult getProductPrice(UUID productId);
    ProductPriceResult getPriceForOrder(UUID productId);
}
