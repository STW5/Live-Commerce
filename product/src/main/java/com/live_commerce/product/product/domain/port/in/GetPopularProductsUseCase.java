package com.live_commerce.product.product.domain.port.in;

import com.live_commerce.product.product.application.dto.result.PopularProductResult;

import java.util.List;

public interface GetPopularProductsUseCase {
    List<PopularProductResult> getPopularProducts();
}
