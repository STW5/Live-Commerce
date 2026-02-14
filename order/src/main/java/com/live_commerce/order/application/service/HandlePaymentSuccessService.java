package com.live_commerce.order.application.service;

import com.live_commerce.order.application.exception.OrderException;
import com.live_commerce.order.application.exception.OrderExceptionCode;
import com.live_commerce.common.saga.SagaStateRepository;
import com.live_commerce.order.domain.model.Order;
import com.live_commerce.order.domain.model.OrderStatus;
import com.live_commerce.order.domain.port.in.HandlePaymentSuccessUseCase;
import com.live_commerce.order.domain.port.out.OrderEventPublisher;
import com.live_commerce.order.domain.port.out.OrderRepositoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * 결제 성공 처리 유즈케이스 구현체
 *
 * [핵사고날 아키텍처 적용]
 * - HandlePaymentSuccessUseCase 인터페이스 구현
 * - OrderRepositoryPort, OrderEventPublisher Port 주입
 * - Kafka Producer 직접 주입 없음
 *
 * [기존 PaymentSuccessServiceKafka.updatePaymentSuccessKafka() 대체]
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HandlePaymentSuccessService implements HandlePaymentSuccessUseCase {

    private final OrderRepositoryPort orderRepositoryPort;
    private final OrderEventPublisher orderEventPublisher;
    private final SagaStateRepository sagaStateRepository;

    @Override
    @Transactional
    public void handle(UUID orderId, String message) {
        // 1. 주문 조회
        Order order = orderRepositoryPort.findById(orderId)
                .orElseThrow(() -> new OrderException(OrderExceptionCode.NOT_FOUND));
        log.info("[HandlePaymentSuccessService] 주문 조회 성공 - orderId: {}", orderId);

        if (message == null) {
            throw new OrderException("결제 상태가 COMPLETED가 아닌 상태입니다. 다시 결제해주세요.",
                    org.springframework.http.HttpStatus.FORBIDDEN);
        }

        // 2. 주문 상태 → PAID
        order.changeStatus(OrderStatus.PAID);
        orderRepositoryPort.save(order);
        log.info("[HandlePaymentSuccessService] 주문 상태 PAID 변경 완료");

        // 3. 재고 감소 이벤트 발행 (Port 통해)
        orderEventPublisher.publishInventoryDecrease(
                order.getId(), order.getProductId(), order.getProductQuantity());
        log.info("[HandlePaymentSuccessService] 재고 감소 이벤트 발행 완료");

        // 4. 쿠폰 사용 이벤트 발행 (쿠폰이 있는 경우)
        if (order.getCouponId() != null) {
            orderEventPublisher.publishCouponUsed(order.getCouponId(), order.getUserId());
            log.info("[HandlePaymentSuccessService] 쿠폰 사용 이벤트 발행 완료");
        }

        // 5. Saga 상태 완료 처리
        updateSagaToCompleted(orderId);
    }

    private void updateSagaToCompleted(UUID orderId) {
        try {
            sagaStateRepository.findByAggregateId(orderId).ifPresent(saga -> {
                saga.complete();
                sagaStateRepository.save(saga);
                log.info("[Saga] 상태 완료 처리 - orderId: {}", orderId);
            });
        } catch (Exception e) {
            log.warn("[Saga] 완료 상태 업데이트 실패 (무시) - orderId: {}, error: {}", orderId, e.getMessage());
        }
    }
}
