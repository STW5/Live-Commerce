package com.live_commerce.product.product.domain.port.out;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

public interface ProductCachePort {
    Optional<Integer> getDiscountPrice(UUID productId);
    void setDiscountPrice(UUID productId, int discountPrice, Duration duration);
}
