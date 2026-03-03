package com.live_commerce.product.product.application.service;

import com.live_commerce.product.product.application.dto.result.ProductPriceResult;
import com.live_commerce.product.product.domain.exception.ProductException;
import com.live_commerce.product.product.domain.model.Product;
import com.live_commerce.product.product.domain.port.in.GetProductPriceUseCase;
import com.live_commerce.product.product.domain.port.out.ProductCachePort;
import com.live_commerce.product.product.domain.port.out.ProductRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GetProductPriceService implements GetProductPriceUseCase {

    private final ProductRepositoryPort productRepositoryPort;
    private final ProductCachePort productCachePort;

    @Transactional(readOnly = true)
    @Override
    public ProductPriceResult getProductPrice(UUID productId) {
        Product product = productRepositoryPort.findById(productId)
                .orElseThrow(ProductException::forProductNotFound);

        Integer currentPrice = productCachePort.getDiscountPrice(productId).orElse(product.getPrice());
        boolean isDiscounted = !currentPrice.equals(product.getPrice());

        return new ProductPriceResult(productId, currentPrice, isDiscounted);
    }

    @Transactional(readOnly = true)
    @Override
    public ProductPriceResult getPriceForOrder(UUID productId) {
        Product product = productRepositoryPort.findById(productId)
                .orElseThrow(ProductException::forProductNotFound);

        Integer currentPrice = productCachePort.getDiscountPrice(productId).orElse(product.getPrice());
        boolean isDiscounted = !currentPrice.equals(product.getPrice());

        return new ProductPriceResult(productId, currentPrice, isDiscounted);
    }
}
