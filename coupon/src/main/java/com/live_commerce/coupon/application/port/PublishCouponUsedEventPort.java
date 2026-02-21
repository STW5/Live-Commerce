package com.live_commerce.coupon.application.port;


import java.util.UUID;

import org.springframework.stereotype.Component;

/**
 * @deprecated 헥사고날 아키텍처 전환으로 {@code domain.port.out.CouponEventPublisher}로 대체.
 */
@Deprecated(since = "hexagonal-ddd-coupon", forRemoval = true)
@Component
public interface PublishCouponUsedEventPort {
  void publishCouponUsedEvent(UUID couponId, UUID userId);
}
