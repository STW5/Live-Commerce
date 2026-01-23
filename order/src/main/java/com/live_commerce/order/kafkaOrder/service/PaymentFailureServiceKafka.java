package com.live_commerce.order.kafkaOrder.service;

import com.live_commerce.order.application.exception.OrderException;
import com.live_commerce.order.application.exception.OrderExceptionCode;
import com.live_commerce.order.domain.model.Order;
import com.live_commerce.order.domain.repository.OrderRepository;
import com.live_commerce.order.kafkaOrder.payment.PaymentFailedEvent;
import com.live_commerce.order.kafkaOrder.product.InventoryEventProducer;
import com.live_commerce.order.kafkaOrder.product.InventoryRollbackEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
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
    private final InventoryEventProducer inventoryEventProducer;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * 결제 실패 이벤트 처리
     * 1. 주문 상태를 FAILED로 변경
     * 2. 재고 롤백 이벤트 발행 (재고 복구)
     * 3. order-failed 이벤트 발행 (결제 서비스에 환불 요청)
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
        log.info("[보상 트랜잭션] 주문 상태 변경: {} -> FAILED", order.getStatus());

        // 4. 재고 롤백 이벤트 발행 (이미 차감된 재고 복구)
        if (order.getProductId() != null && order.getProductQuantity() > 0) {
            InventoryRollbackEvent rollbackEvent = new InventoryRollbackEvent(
                    orderId,
                    order.getProductId(),
                    order.getProductQuantity()
            );
            inventoryEventProducer.sendInventoryRollbackEvent(rollbackEvent);
            log.info("[보상 트랜잭션] 재고 롤백 이벤트 발행 - productId: {}, quantity: {}",
                    order.getProductId(), order.getProductQuantity());
        }

        // 5. order-failed 이벤트 발행 (결제 서비스에 환불 요청)
        // 이미 결제가 완료된 상태에서 실패한 경우 환불이 필요
        OrderFailedEvent orderFailedEvent = new OrderFailedEvent(
                orderId,
                "주문 처리 실패: " + failureMessage
        );
        kafkaTemplate.send("order-failed", orderId.toString(), orderFailedEvent);
        log.info("[보상 트랜잭션] order-failed 이벤트 발행 - orderId: {}", orderId);

        // 6. 쿠폰 복구는 쿠폰 서비스에서 order-failed 이벤트를 수신하여 처리
        // (현재는 쿠폰 사용 취소 로직이 없으므로 향후 구현 필요)

        log.info("[보상 트랜잭션] 결제 실패 처리 완료 - orderId: {}", orderId);
    }

    /**
     * order-failed 이벤트 record
     */
    public record OrderFailedEvent(
            UUID orderId,
            String message
    ) {}
}
