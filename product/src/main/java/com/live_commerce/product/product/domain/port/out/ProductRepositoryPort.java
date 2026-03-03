package com.live_commerce.product.product.domain.port.out;

import com.live_commerce.product.product.domain.model.Product;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductRepositoryPort {
    Product save(Product product);
    Optional<Product> findById(UUID productId);
    boolean existsById(UUID productId);
    List<Product> findAllByIds(List<UUID> productIds);
}
