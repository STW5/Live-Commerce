package com.live_commerce.order.application.service;

import com.live_commerce.common.saga.SagaStateRepository;
import com.live_commerce.order.application.exception.OrderException;
import com.live_commerce.order.application.exception.OrderExceptionCode;
import com.live_commerce.order.domain.model.Order;
import com.live_commerce.order.domain.model.OrderStatus;
import com.live_commerce.order.domain.port.in.HandlePaymentFailureUseCase;
import com.live_commerce.order.domain.port.out.OrderEventPublisher;
import com.live_commerce.order.domain.port.out.OrderRepositoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * 결제 실패 처리 유즈케이스 구현체 (보상 트랜잭션)
 *
 * [핵사고날 아키텍처 적용]
 * - HandlePaymentFailureUseCase 인터페이스 구현
 * - OrderEventPublisher Port로 Outbox 이벤트 발행
 * - Kafka Producer / OutboxEventHelper 직접 주입 없음
 *
 * [기존 PaymentFailureServiceKafka.handlePaymentFailure() 대체]
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HandlePaymentFailureService implements HandlePaymentFailureUseCase {

    private final OrderRepositoryPort orderRepositoryPort;
    private final OrderEventPublisher orderEventPublisher;
    private final SagaStateRepository sagaStateRepository;

    @Override
    @Transactional
    public void handle(UUID orderId, String failureMessage) {
        log.info("[보상 트랜잭션] 결제 실패 처리 시작 - orderId: {}, message: {}", orderId, failureMessage);

        // 1. 주문 조회
        Order order = orderRepositoryPort.findById(orderId)
                .orElseThrow(() -> new OrderException(OrderExceptionCode.NOT_FOUND));

        // 2. 멱등성 보장 - 이미 FAILED 처리된 주문 무시
        if (order.getStatus() == OrderStatus.FAILED) {
            log.info("[보상 트랜잭션] 이미 실패 처리된 주문 - orderId: {}", orderId);
            return;
        }

        // 3. 주문 상태 → FAILED
        order.changeStatus(OrderStatus.FAILED);
        orderRepositoryPort.save(order);
        log.info("[보상 트랜잭션] 주문 상태 변경 -> FAILED");

        // 4. Saga 상태 업데이트
        updateSagaToCompensating(orderId, failureMessage);

        // 5. 재고 롤백 이벤트 발행 (Outbox 통해 원자적 처리)
        if (order.getProductId() != null && order.getProductQuantity() > 0) {
            orderEventPublisher.publishInventoryRollback(
                    orderId, order.getProductId(), order.getProductQuantity(),
                    "결제 실패: " + failureMessage);
            log.info("[보상 트랜잭션] 재고 롤백 이벤트 Outbox 저장");
        }

        // 6. order-failed 이벤트 발행 (환불 + 쿠폰 복구 트리거)
        orderEventPublisher.publishOrderFailed(orderId, failureMessage);
        log.info("[보상 트랜잭션] order-failed 이벤트 Outbox 저장");

        log.info("[보상 트랜잭션] 결제 실패 처리 완료 - orderId: {}", orderId);
    }

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
