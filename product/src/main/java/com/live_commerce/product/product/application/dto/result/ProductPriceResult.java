package com.live_commerce.product.product.application.dto.result;

import java.util.UUID;

public record ProductPriceResult(
        UUID productId,
        Integer currentPrice,
        boolean isDiscounted
) {}
