package com.live_commerce.order.kafkaOrder.service;

import com.live_commerce.common.saga.SagaState;
import com.live_commerce.common.saga.SagaStateRepository;
import com.live_commerce.events.inventory.InventoryRollbackEvent;
import com.live_commerce.events.order.OrderFailedEvent;
import com.live_commerce.events.payment.PaymentFailedEvent;
import com.live_commerce.order.application.exception.OrderException;
import com.live_commerce.order.application.exception.OrderExceptionCode;
import com.live_commerce.order.domain.model.Order;
import com.live_commerce.order.domain.repository.OrderRepository;
import com.live_commerce.order.infrastructure.outbox.OutboxEventHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static com.live_commerce.order.domain.model.OrderStatus.FAILED;

/**
 * 결제 실패 시 보상 트랜잭션 처리 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentFailureServiceKafka {

    private final OrderRepository orderRepository;
    private final OutboxEventHelper outboxEventHelper;
    private final SagaStateRepository sagaStateRepository;

    /**
     * 결제 실패 이벤트 처리 (보상 트랜잭션)
     * 1. 주문 상태를 FAILED로 변경
     * 2. 재고 롤백 이벤트를 Outbox에 저장 (원자적)
     * 3. order-failed 이벤트를 Outbox에 저장 (원자적)
     * → OutboxEventPublisher가 비동기로 Kafka 발행
     */
    @Transactional
    public void handlePaymentFailure(PaymentFailedEvent event) {
        UUID orderId = event.orderId();
        String failureMessage = event.message();

        log.info("[보상 트랜잭션] 결제 실패 처리 시작 - orderId: {}, message: {}", orderId, failureMessage);

        // 1. 주문 조회
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderException(OrderExceptionCode.NOT_FOUND));

        // 2. 이미 실패 처리된 주문인지 확인 (멱등성 보장)
        if (order.getStatus() == FAILED) {
            log.info("[보상 트랜잭션] 이미 실패 처리된 주문 - orderId: {}", orderId);
            return;
        }

        // 3. 주문 상태를 FAILED로 변경
        order.changeStatus(FAILED);
        log.info("[보상 트랜잭션] 주문 상태 변경 -> FAILED");

        // 3-1. Saga 상태 업데이트 (FAILED → COMPENSATING)
        updateSagaToCompensating(orderId, failureMessage);

        // 4. 재고 롤백 이벤트를 Outbox에 저장 (DB 트랜잭션과 원자적)
        if (order.getProductId() != null && order.getProductQuantity() > 0) {
            InventoryRollbackEvent rollbackEvent = InventoryRollbackEvent.of(
                    orderId,
                    order.getProductId(),
                    order.getProductQuantity(),
                    "결제 실패: " + failureMessage
            );
            outboxEventHelper.saveEvent("ORDER", orderId, "INVENTORY_ROLLBACK",
                    "inventory-rollback", rollbackEvent);
            log.info("[보상 트랜잭션] 재고 롤백 이벤트 Outbox 저장 - productId: {}, quantity: {}",
                    order.getProductId(), order.getProductQuantity());
        }

        // 5. order-failed 이벤트를 Outbox에 저장 (환불 + 쿠폰 복구 트리거)
        OrderFailedEvent orderFailedEvent = OrderFailedEvent.of(orderId, "주문 처리 실패: " + failureMessage);
        outboxEventHelper.saveEvent("ORDER", orderId, "ORDER_FAILED",
                "order-failed", orderFailedEvent);
        log.info("[보상 트랜잭션] order-failed 이벤트 Outbox 저장 - orderId: {}", orderId);

        log.info("[보상 트랜잭션] 결제 실패 처리 완료 (Outbox 저장됨, 비동기 발행 대기)");
    }

    /**
     * Saga 상태를 FAILED → COMPENSATING으로 전환
     */
    private void updateSagaToCompensating(UUID orderId, String failureMessage) {
        try {
            sagaStateRepository.findByAggregateId(orderId).ifPresent(saga -> {
                saga.fail(failureMessage);
                saga.startCompensation();
                sagaStateRepository.save(saga);
                log.info("[Saga] 상태 변경: FAILED → COMPENSATING - orderId: {}", orderId);
            });
        } catch (Exception e) {
            log.warn("[Saga] 상태 업데이트 실패 (무시) - orderId: {}, error: {}", orderId, e.getMessage());
        }
    }
}
