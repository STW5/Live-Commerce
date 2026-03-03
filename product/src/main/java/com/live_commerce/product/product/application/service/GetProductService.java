package com.live_commerce.product.product.application.service;

import com.live_commerce.product.product.application.dto.result.ProductResult;
import com.live_commerce.product.product.domain.exception.ProductException;
import com.live_commerce.product.product.domain.model.Product;
import com.live_commerce.product.product.domain.port.in.GetProductUseCase;
import com.live_commerce.product.product.domain.port.out.InventoryQueryPort;
import com.live_commerce.product.product.domain.port.out.ProductRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GetProductService implements GetProductUseCase {

    private final ProductRepositoryPort productRepositoryPort;
    private final InventoryQueryPort inventoryQueryPort;

    @Transactional(readOnly = true)
    @Override
    public ProductResult getProduct(UUID productId) {
        Product product = productRepositoryPort.findById(productId)
                .orElseThrow(ProductException::forProductNotFound);

        boolean soldOut = inventoryQueryPort.isSoldOut(productId);
        return ProductResult.from(product, soldOut);
    }
}
