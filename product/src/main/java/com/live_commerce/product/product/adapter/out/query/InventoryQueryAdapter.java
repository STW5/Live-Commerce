package com.live_commerce.product.product.adapter.out.query;

import com.live_commerce.product.inventory.adapter.out.persistence.InventoryJpaRepository;
import com.live_commerce.product.product.domain.port.out.InventoryQueryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * cross-subdomain query adapter: product subdomain → inventory 재고 상태 조회
 */
@Component
@RequiredArgsConstructor
public class InventoryQueryAdapter implements InventoryQueryPort {

    private final InventoryJpaRepository inventoryJpaRepository;

    @Override
    public boolean isSoldOut(UUID productId) {
        return inventoryJpaRepository.findByProductIdAndDeletedStatusFalse(productId)
                .map(inv -> inv.getAvailableQuantity() <= 0)
                .orElse(true);
    }
}
