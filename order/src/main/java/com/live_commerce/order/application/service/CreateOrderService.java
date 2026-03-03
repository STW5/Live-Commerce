package com.live_commerce.order.application.service;

import com.live_commerce.order.application.dto.command.CreateOrderCommand;
import com.live_commerce.order.application.dto.result.CreateOrderResult;
import com.live_commerce.order.application.dto.result.ProductInfo;
import com.live_commerce.order.domain.exception.OrderDomainException;
import com.live_commerce.order.domain.model.Order;
import com.live_commerce.order.domain.model.vo.DiscountPolicy;
import com.live_commerce.order.domain.model.vo.OrderQuantity;
import com.live_commerce.common.saga.SagaState;
import com.live_commerce.common.saga.SagaStateRepository;
import com.live_commerce.order.domain.port.in.CreateOrderUseCase;
import com.live_commerce.order.domain.port.out.BroadcastQueryPort;
import com.live_commerce.order.domain.port.out.CouponQueryPort;
import com.live_commerce.order.domain.port.out.OrderRepositoryPort;
import com.live_commerce.order.domain.port.out.ProductQueryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 주문 생성 유즈케이스 구현체
 *
 * [핵사고날 아키텍처 적용]
 * - CreateOrderUseCase 인터페이스 구현 (Inbound Port)
 * - Port 인터페이스만 주입 (Feign 클래스 import 없음)
 * - 할인 계산 로직 → DiscountPolicy VO로 위임
 * - Order.create() 정적 팩토리 사용 (DDD 패턴 완성)
 *
 * [기존 OrderCreateService.orderCreator() 대체]
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CreateOrderService implements CreateOrderUseCase {

    // Port(인터페이스)만 주입 - Feign, JPA 클래스 import 없음
    private final BroadcastQueryPort broadcastQueryPort;
    private final ProductQueryPort productQueryPort;
    private final CouponQueryPort couponQueryPort;
    private final OrderRepositoryPort orderRepositoryPort;
    private final SagaStateRepository sagaStateRepository;

    @Override
    @Transactional
    public CreateOrderResult createOrder(CreateOrderCommand command) {

        // 1. 방송 중인지 확인
        if (!broadcastQueryPort.isLive(command.broadcastId())) {
            throw new OrderDomainException("방송 중일 때만 주문이 가능합니다.");
        }
        log.info("[CreateOrderService] 방송 확인 완료");

        // 2. 상품 정보 조회
        ProductInfo product = productQueryPort.findById(command.productId());
        if (product == null) {
            throw new OrderDomainException("해당 상품이 존재하지 않습니다.");
        }
        log.info("[CreateOrderService] 상품 조회 완료: {}", product.name());

        // 3. 재고 확인
        if (!productQueryPort.isOrderable(command.productId(), command.orderQuantity())) {
            throw new OrderDomainException("재고가 없는 상태입니다.");
        }
        log.info("[CreateOrderService] 재고 확인 완료");

        // 4. 할인 정책 조회 (쿠폰이 있는 경우)
        DiscountPolicy discountPolicy = null;
        if (command.couponId() != null) {
            if (!couponQueryPort.userHasCoupon(command.userId(), command.couponId())) {
                throw new OrderDomainException("보유하지 않은 쿠폰입니다.");
            }
            String couponCode = couponQueryPort.getCouponCode(command.userId(), command.couponId());
            if (couponCode != null) {
                discountPolicy = couponQueryPort.getDiscountPolicy(couponCode);
            }
        }
        log.info("[CreateOrderService] 할인 정책 조회 완료");

        // 5. Order.create() 정적 팩토리 사용 - DDD 패턴, 금액 계산 Order 내부 위임
        Order order = Order.create(
                command.userId(),
                command.productId(),
                command.broadcastId(),
                new OrderQuantity(command.orderQuantity()),
                product.unitPrice(),
                command.couponId(),
                discountPolicy
        );

        // requirement 설정 (create() 시그니처에 포함되지 않는 선택적 필드)
        if (command.requirement() != null) {
            order.setRequirement(command.requirement());
        }

        // 6. 주문 저장
        Order savedOrder = orderRepositoryPort.save(order);
        log.info("[CreateOrderService] 주문 생성 완료: {}", savedOrder.getId());

        // 7. Saga 추적 시작 (Best Effort - 실패해도 주문 생성에 영향 없음)
        try {
            SagaState saga = SagaState.start("ORDER_CREATION", savedOrder.getId(), null);
            sagaStateRepository.save(saga);
            log.info("[CreateOrderService] SagaState 시작 - orderId: {}", savedOrder.getId());
        } catch (Exception e) {
            log.warn("[CreateOrderService] SagaState 저장 실패 (무시) - orderId: {}, reason: {}",
                    savedOrder.getId(), e.getMessage());
        }

        return CreateOrderResult.from(savedOrder);
    }
}
