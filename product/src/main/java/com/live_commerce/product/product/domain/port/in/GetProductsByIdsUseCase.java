package com.live_commerce.product.product.domain.port.in;

import com.live_commerce.product.product.application.dto.result.ProductSummaryResult;

import java.util.List;
import java.util.UUID;

public interface GetProductsByIdsUseCase {
    List<ProductSummaryResult> getProductsByIds(List<UUID> productIds);
}
