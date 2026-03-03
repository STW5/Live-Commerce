package com.live_commerce.product.product.adapter.out.persistence;

import com.live_commerce.product.product.domain.model.ProductDiscount;
import com.live_commerce.product.product.domain.port.out.ProductDiscountRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProductDiscountPersistenceAdapter implements ProductDiscountRepositoryPort {

    private final ProductDiscountJpaRepository productDiscountJpaRepository;

    @Override
    public ProductDiscount save(ProductDiscount productDiscount) {
        ProductDiscountJpaEntity entity = ProductDiscountJpaEntity.from(productDiscount);
        return productDiscountJpaRepository.save(entity).toDomain();
    }
}
