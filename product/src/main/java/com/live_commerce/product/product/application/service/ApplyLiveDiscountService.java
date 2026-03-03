package com.live_commerce.product.product.application.service;

import com.live_commerce.product.product.domain.exception.ProductException;
import com.live_commerce.product.product.domain.model.Product;
import com.live_commerce.product.product.domain.model.ProductDiscount;
import com.live_commerce.product.product.domain.port.in.ApplyLiveDiscountUseCase;
import com.live_commerce.product.product.domain.port.out.ProductCachePort;
import com.live_commerce.product.product.domain.port.out.ProductDiscountRepositoryPort;
import com.live_commerce.product.product.domain.port.out.ProductRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ApplyLiveDiscountService implements ApplyLiveDiscountUseCase {

    private final ProductRepositoryPort productRepositoryPort;
    private final ProductDiscountRepositoryPort productDiscountRepositoryPort;
    private final ProductCachePort productCachePort;

    @Transactional
    @Override
    public void applyLiveDiscount(UUID productId, int discountPrice, Duration duration, UUID appliedBy) {
        Product product = productRepositoryPort.findById(productId)
                .orElseThrow(ProductException::forProductNotFound);

        if (discountPrice >= product.getPrice()) {
            throw new IllegalArgumentException("할인 가격은 원래 가격보다 낮아야 합니다.");
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime endAt = now.plus(duration);

        ProductDiscount discount = ProductDiscount.create(productId, discountPrice, now, endAt, appliedBy);
        productDiscountRepositoryPort.save(discount);

        productCachePort.setDiscountPrice(productId, discountPrice, duration);
    }
}
