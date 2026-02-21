package com.live_commerce.coupon.infrastructure.kafka.consumer;

import com.live_commerce.coupon.application.service.IssuedCouponService;
import com.live_commerce.coupon.infrastructure.kafka.event.FirstJoinCouponEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * @deprecated 헥사고날 아키텍처 전환으로 {@code adapter.in.kafka.FirstJoinCouponKafkaConsumer}로 대체됨.
 *             중복 처리 방지를 위해 @Component 비활성화.
 */
@Deprecated(since = "hexagonal-ddd-coupon", forRemoval = true)
@Slf4j
// @Component  // 비활성화: FirstJoinCouponKafkaConsumer (adapter.in.kafka) 로 대체
@RequiredArgsConstructor
public class FirstJoinCouponEventConsumer {

  private final IssuedCouponService issuedCouponService;

  @KafkaListener(
      topics = "first-join-coupon",
      groupId = "${spring.application.name}"
  )
  public void onFirstJoin(FirstJoinCouponEvent msg) {
    issuedCouponService.issueFirstCouponDirectly(msg.userId());
    log.info("✅ 회원가입 쿠폰이 정상 발급되었습니다.(kafka): userId={}", msg.userId());

  }
}
