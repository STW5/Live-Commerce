package com.live_commerce.product.inventory.domain.port.in;

import com.live_commerce.product.inventory.application.dto.result.InventoryQuantityResult;

import java.util.UUID;

public interface CheckInventoryQuantityUseCase {
    InventoryQuantityResult checkInventoryQuantity(UUID productId);
}
