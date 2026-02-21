package com.live_commerce.coupon.infrastructure.kafka.consumer;

import com.live_commerce.coupon.application.service.IssuedCouponService;
import com.live_commerce.coupon.infrastructure.kafka.event.CouponUsedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * @deprecated 헥사고날 아키텍처 전환으로 {@code adapter.in.kafka.CouponUsedKafkaConsumer}로 대체됨.
 *             중복 처리 방지를 위해 @Component 비활성화.
 */
@Deprecated(since = "hexagonal-ddd-coupon", forRemoval = true)
@Slf4j
// @Component  // 비활성화: CouponUsedKafkaConsumer (adapter.in.kafka) 로 대체
@RequiredArgsConstructor
public class CouponUsedEventConsumer {

  private final IssuedCouponService issuedCouponService;

  @KafkaListener(
          topics = "coupon-used",
          groupId = "${spring.application.name}"
  )
  public void onCouponUsed(CouponUsedEvent msg) {
    issuedCouponService.handleCouponUsedEvent(msg.couponId(), msg.userId());
    log.info("✅ 쿠폰 사용 성공 이벤트 수신(kafka): couponId={}, userId={}", msg.couponId(), msg.userId());
  }
}