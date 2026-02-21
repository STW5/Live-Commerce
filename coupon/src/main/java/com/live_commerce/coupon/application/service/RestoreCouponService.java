package com.live_commerce.coupon.application.service;

import com.live_commerce.coupon.domain.exception.IssuedCouponException;
import com.live_commerce.coupon.domain.model.IssuedCoupon;
import com.live_commerce.coupon.domain.port.in.RestoreCouponUseCase;
import com.live_commerce.coupon.domain.port.out.IssuedCouponRepositoryPort;
import com.live_commerce.coupon.domain.port.out.OrderQueryPort;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 쿠폰 복구 유즈케이스 구현체 (보상 트랜잭션)
 *
 * [헥사고날 아키텍처 적용]
 * - OrderClient(Feign) 직접 의존 → OrderQueryPort 인터페이스로 대체
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RestoreCouponService implements RestoreCouponUseCase {

    private final IssuedCouponRepositoryPort issuedCouponRepositoryPort;
    private final OrderQueryPort orderQueryPort;

    @Override
    @Transactional
    public void restoreCouponByOrderFailed(UUID orderId) {
        log.info("[보상 트랜잭션] 쿠폰 복구 시작 - orderId: {}", orderId);

        OrderQueryPort.OrderInfo order = orderQueryPort.getOrder(orderId);

        if (order.couponId() == null) {
            log.info("[보상 트랜잭션] 쿠폰을 사용하지 않은 주문 - orderId: {}", orderId);
            return;
        }

        IssuedCoupon coupon = issuedCouponRepositoryPort
                .findByIdAndUserId(order.couponId(), order.userId())
                .orElseThrow(() -> {
                    log.error("[보상 트랜잭션] 쿠폰을 찾을 수 없음 - couponId: {}, userId: {}",
                            order.couponId(), order.userId());
                    IssuedCouponException.issuedCouponNotFound();
                    return null;
                });

        if (!coupon.isUsed()) {
            log.info("[보상 트랜잭션] 이미 미사용 상태인 쿠폰 - couponId: {}", order.couponId());
            return;
        }

        coupon.restoreCoupon();
        issuedCouponRepositoryPort.save(coupon);
        log.info("[보상 트랜잭션] 쿠폰 복구 완료 - couponId: {}, orderId: {}", order.couponId(), orderId);
    }
}
