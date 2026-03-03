package com.live_commerce.product.inventory.application.service;

import com.live_commerce.product.inventory.application.dto.request.InventoryCreateRequestDto;
import com.live_commerce.product.inventory.application.dto.result.InventoryResult;
import com.live_commerce.product.inventory.domain.exception.InventoryException;
import com.live_commerce.product.inventory.domain.model.Inventory;
import com.live_commerce.product.inventory.domain.port.in.CreateInventoryUseCase;
import com.live_commerce.product.inventory.domain.port.out.InventoryRepositoryPort;
import com.live_commerce.product.product.domain.port.out.ProductRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CreateInventoryService implements CreateInventoryUseCase {

    private final InventoryRepositoryPort inventoryRepositoryPort;
    private final ProductRepositoryPort productRepositoryPort;

    @Transactional
    @Override
    public InventoryResult createInventory(InventoryCreateRequestDto requestDto) {
        if (!productRepositoryPort.existsById(requestDto.productId())) {
            throw InventoryException.forProductNotFound();
        }

        Inventory inventory = Inventory.create(
                requestDto.productId(),
                requestDto.quantity(),
                requestDto.reservedQuantity(),
                requestDto.availableQuantity(),
                requestDto.inventoryStatus()
        );

        Inventory saved = inventoryRepositoryPort.save(inventory);
        return InventoryResult.from(saved);
    }
}
