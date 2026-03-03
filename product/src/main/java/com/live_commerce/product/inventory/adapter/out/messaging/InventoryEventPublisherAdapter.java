package com.live_commerce.product.inventory.adapter.out.messaging;

import com.live_commerce.events.inventory.InventoryDecreasedEvent;
import com.live_commerce.events.inventory.InventorySoldOutEvent;
import com.live_commerce.product.inventory.domain.port.out.InventoryEventPublisherPort;
import com.live_commerce.product.product.infrastructure.outbox.OutboxEventHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class InventoryEventPublisherAdapter implements InventoryEventPublisherPort {

    private final OutboxEventHelper outboxEventHelper;

    @Override
    public void publishInventoryDecreased(UUID orderId, UUID productId, int quantity) {
        InventoryDecreasedEvent event = InventoryDecreasedEvent.of(orderId, productId, quantity);
        outboxEventHelper.saveEvent("INVENTORY", orderId,
                "INVENTORY_DECREASED", "inventory-decreased", event);
        log.info("[InventoryEventPublisherAdapter] inventory-decreased Outbox 저장 - orderId: {}", orderId);
    }

    @Override
    public void publishInventorySoldOut(UUID productId) {
        InventorySoldOutEvent event = InventorySoldOutEvent.of(productId);
        outboxEventHelper.saveEvent("INVENTORY", productId,
                "INVENTORY_SOLD_OUT", "inventory-sold-out", event);
        log.info("[InventoryEventPublisherAdapter] inventory-sold-out Outbox 저장 - productId: {}", productId);
    }
}
