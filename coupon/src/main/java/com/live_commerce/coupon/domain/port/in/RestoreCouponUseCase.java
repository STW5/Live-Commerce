package com.live_commerce.coupon.domain.port.in;

import java.util.UUID;

/**
 * 쿠폰 복구 UseCase (보상 트랜잭션)
 * 주문 실패 시 사용된 쿠폰을 미사용 상태로 복원
 */
public interface RestoreCouponUseCase {
    void restoreCouponByOrderFailed(UUID orderId);
}
