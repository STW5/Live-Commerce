package com.live_commerce.order.adapter.out.messaging;

import com.live_commerce.events.inventory.InventoryDecreaseRequestEvent;
import com.live_commerce.events.inventory.InventoryRollbackEvent;
import com.live_commerce.order.domain.port.out.InventoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * InventoryPort 구현체 (Kafka Messaging Adapter)
 * - 재고 감소 / 복구 이벤트를 Kafka로 직접 발행
 * - Topic: inventory-decrease, inventory-rollback
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaInventoryEventPublisher implements InventoryPort {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Override
    public void decreaseInventory(UUID productId, int quantity, UUID orderId) {
        InventoryDecreaseRequestEvent event = InventoryDecreaseRequestEvent.of(orderId, productId, quantity);
        kafkaTemplate.send("inventory-decrease", orderId.toString(), event);
        log.info("[KafkaInventoryEventPublisher] 재고 감소 이벤트 발행 - orderId: {}, productId: {}", orderId, productId);
    }

    @Override
    public void rollbackInventory(UUID productId, int quantity, UUID orderId) {
        InventoryRollbackEvent event = InventoryRollbackEvent.of(orderId, productId, quantity, "재고 복구");
        kafkaTemplate.send("inventory-rollback", orderId.toString(), event);
        log.info("[KafkaInventoryEventPublisher] 재고 롤백 이벤트 발행 - orderId: {}, productId: {}", orderId, productId);
    }
}
