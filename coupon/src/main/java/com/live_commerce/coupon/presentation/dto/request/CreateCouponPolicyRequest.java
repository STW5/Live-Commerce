package com.live_commerce.coupon.presentation.dto.request;

import com.live_commerce.coupon.domain.model.CouponPolicy;
import com.live_commerce.coupon.domain.model.DISCOUNT_TYPE;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CreateCouponPolicyRequest(
    @NotNull(message = "쿠폰 코드가 누락되었습니다.") String code,
    @NotNull(message = "쿠폰 이름이 누락되었습니다.") String name,
    @NotNull(message = "할인 종류가 누락되었습니다.") DISCOUNT_TYPE discountType,
    @NotNull(message = "할인 금액이 누락되었습니다.") BigDecimal discountValue,
    @NotNull(message = "최소 주문 금액이 누락되었습니다.") BigDecimal minOrderAmt,
    @NotNull(message = "최대 주문 금액이 누락되었습니다.") BigDecimal maxOrderAmt,
    @NotNull(message = "쿠폰 시작 시간이 누락되었습니다.") LocalDateTime startAt,
    @NotNull(message = "쿠폰 종료 시간이 누락되었습니다.") LocalDateTime endAt,
    @NotNull(message = "쿠폰 활성 여부가 누락되었습니다.") boolean isActive
) {

  public CouponPolicy toCouponPolicy() {
    return CouponPolicy.create(
        this.code(), this.name(), this.discountType(),
        this.discountValue(), this.minOrderAmt(), this.maxOrderAmt(),
        this.startAt(), this.endAt(), this.isActive()
    );
  }
}