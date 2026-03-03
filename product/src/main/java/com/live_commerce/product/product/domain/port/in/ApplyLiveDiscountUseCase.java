package com.live_commerce.product.product.domain.port.in;

import java.time.Duration;
import java.util.UUID;

public interface ApplyLiveDiscountUseCase {
    void applyLiveDiscount(UUID productId, int discountPrice, Duration duration, UUID appliedById);
}
