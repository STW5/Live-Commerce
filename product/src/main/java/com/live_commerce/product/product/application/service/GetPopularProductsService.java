package com.live_commerce.product.product.application.service;

import com.live_commerce.product.product.application.dto.result.PopularProductResult;
import com.live_commerce.product.product.domain.exception.ProductException;
import com.live_commerce.product.product.domain.model.Product;
import com.live_commerce.product.product.domain.port.in.GetPopularProductsUseCase;
import com.live_commerce.product.product.domain.port.out.ProductRepositoryPort;
import com.live_commerce.product.inventory.domain.port.out.InventoryCachePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.AbstractMap;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GetPopularProductsService implements GetPopularProductsUseCase {

    private final ProductRepositoryPort productRepositoryPort;
    private final InventoryCachePort inventoryCachePort;

    @Override
    public List<PopularProductResult> getPopularProducts() {
        List<AbstractMap.SimpleEntry<UUID, Long>> topSoldCounts = inventoryCachePort.getTopSoldCounts(10);
        if (topSoldCounts.isEmpty()) {
            return List.of();
        }

        List<PopularProductResult> result = new ArrayList<>();
        for (AbstractMap.SimpleEntry<UUID, Long> entry : topSoldCounts) {
            productRepositoryPort.findById(entry.getKey()).ifPresent(product ->
                    result.add(PopularProductResult.from(product, entry.getValue()))
            );
        }
        return result;
    }
}
