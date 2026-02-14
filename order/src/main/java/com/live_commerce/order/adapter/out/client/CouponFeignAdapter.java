package com.live_commerce.order.adapter.out.client;

import com.live_commerce.order.domain.model.DISCOUNT_TYPE;
import com.live_commerce.order.domain.model.vo.DiscountPolicy;
import com.live_commerce.order.domain.port.out.CouponQueryPort;
import com.live_commerce.order.infrastructure.client.feign.CouponClient;
import com.live_commerce.order.infrastructure.client.response.IssuedCouponListResponse;
import com.live_commerce.order.infrastructure.client.response.ReadCouponPolicyResponse;
import com.live_commerce.order.presentation.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * CouponQueryPort 구현체
 * - Feign 응답 DTO → Domain VO(DiscountPolicy) 변환
 * - DISCOUNT_TYPE(기존 enum) → DiscountPolicy.Type(새 VO enum) 매핑
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CouponFeignAdapter implements CouponQueryPort {

    private final CouponClient couponClient;

    @Override
    public boolean userHasCoupon(UUID userId, UUID couponId) {
        ApiResponse<IssuedCouponListResponse> response = couponClient.getIssuedCoupons();
        IssuedCouponListResponse couponList = response.getData();
        if (couponList == null || couponList.coupons() == null) {
            return false;
        }
        return couponList.coupons().stream()
                .anyMatch(c -> c.id().equals(couponId));
    }

    @Override
    public DiscountPolicy getDiscountPolicy(String couponCode) {
        ApiResponse<ReadCouponPolicyResponse> response = couponClient.getCouponPolicy(couponCode);
        ReadCouponPolicyResponse policy = response.getData();

        // Feign DTO의 DISCOUNT_TYPE → Domain VO의 DiscountPolicy.Type 변환
        DiscountPolicy.Type type = (policy.discountType() == DISCOUNT_TYPE.FIXED)
                ? DiscountPolicy.Type.FIXED
                : DiscountPolicy.Type.RATE;

        return new DiscountPolicy(type, BigDecimal.valueOf(policy.discountValue()));
    }

    @Override
    public String getCouponCode(UUID userId, UUID couponId) {
        ApiResponse<IssuedCouponListResponse> response = couponClient.getIssuedCoupons();
        IssuedCouponListResponse couponList = response.getData();
        if (couponList == null || couponList.coupons() == null) {
            return null;
        }
        return couponList.coupons().stream()
                .filter(c -> c.id().equals(couponId))
                .map(c -> c.couponCode())
                .findFirst()
                .orElse(null);
    }
}
