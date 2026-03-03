package com.live_commerce.product.product.adapter.out.persistence;

import com.live_commerce.product.product.domain.model.Product;
import com.live_commerce.product.product.domain.port.out.ProductRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ProductPersistenceAdapter implements ProductRepositoryPort {

    private final ProductJpaRepository productJpaRepository;

    @Override
    public Product save(Product product) {
        ProductJpaEntity entity = ProductJpaEntity.from(product);
        return productJpaRepository.save(entity).toDomain();
    }

    @Override
    public Optional<Product> findById(UUID productId) {
        return productJpaRepository.findByProductIdAndDeletedStatusFalse(productId)
                .map(ProductJpaEntity::toDomain);
    }

    @Override
    public boolean existsById(UUID productId) {
        return productJpaRepository.existsByProductIdAndDeletedStatusFalse(productId);
    }

    @Override
    public List<Product> findAllByIds(List<UUID> productIds) {
        return productJpaRepository.findAllByProductIdInAndDeletedStatusFalse(productIds)
                .stream()
                .map(ProductJpaEntity::toDomain)
                .toList();
    }
}
