package com.live_commerce.order.domain.port.out;

import com.live_commerce.order.domain.model.vo.DiscountPolicy;

import java.util.UUID;

/**
 * 쿠폰 서비스 조회 포트 (Outbound)
 * - 구현체: adapter/out/client/CouponFeignAdapter
 * - 어댑터가 Feign 응답 DTO → Domain VO(DiscountPolicy) 변환 책임
 */
public interface CouponQueryPort {
    /**
     * 유저가 해당 쿠폰을 보유하고 있는지 확인
     */
    boolean userHasCoupon(UUID userId, UUID couponId);

    /**
     * 쿠폰 코드로 할인 정책 조회 → Domain VO 반환
     * (Feign DTO → DiscountPolicy 변환은 어댑터 책임)
     */
    DiscountPolicy getDiscountPolicy(String couponCode);

    /**
     * 유저의 보유 쿠폰 중 특정 쿠폰의 couponCode 조회
     */
    String getCouponCode(UUID userId, UUID couponId);
}
