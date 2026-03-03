package com.live_commerce.product.inventory.domain.port.in;

import com.live_commerce.product.inventory.application.dto.result.InventoryResult;
import com.live_commerce.product.inventory.application.dto.request.InventoryCreateRequestDto;

public interface CreateInventoryUseCase {
    InventoryResult createInventory(InventoryCreateRequestDto requestDto);
}
