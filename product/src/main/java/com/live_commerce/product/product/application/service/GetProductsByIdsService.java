package com.live_commerce.product.product.application.service;

import com.live_commerce.product.product.application.dto.result.ProductSummaryResult;
import com.live_commerce.product.product.domain.exception.ProductException;
import com.live_commerce.product.product.domain.port.in.GetProductsByIdsUseCase;
import com.live_commerce.product.product.domain.port.out.ProductRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GetProductsByIdsService implements GetProductsByIdsUseCase {

    private final ProductRepositoryPort productRepositoryPort;

    private static final int MAX_PRODUCT_IDS = 100;

    @Override
    public List<ProductSummaryResult> getProductsByIds(List<UUID> productIds) {
        if (productIds.size() > MAX_PRODUCT_IDS) {
            throw ProductException.exceedsMaxRequestLimit();
        }

        return productRepositoryPort.findAllByIds(productIds).stream()
                .map(ProductSummaryResult::from)
                .toList();
    }
}
