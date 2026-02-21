package com.live_commerce.coupon.domain.model;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static reactor.core.publisher.Mono.when;

import com.live_commerce.coupon.domain.exception.CouponDiscountTypeException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.*;

public class CouponPolicyTest {

  private String code;
  private String name;
  private DISCOUNT_TYPE discountType;
  private BigDecimal discountValue;
  private BigDecimal minOrderAmt;
  private BigDecimal maxOrderAmt;
  private LocalDateTime startAt;
  private LocalDateTime endAt;
  private boolean isActive;

  @BeforeEach
  void setUp() {
    code = "SUMMER_SALE_100";
    name = "테스트 쿠폰";
    discountType = DISCOUNT_TYPE.FIXED;
    discountValue = BigDecimal.valueOf(0.1);
    minOrderAmt = BigDecimal.valueOf(100.00);
    maxOrderAmt = BigDecimal.valueOf(1000.00);
    startAt = LocalDateTime.now();
    endAt = startAt.plusDays(30);
    isActive = true;
  }

  private CouponPolicy createCouponPolicy() {
    return CouponPolicy.create(code, name, discountType, discountValue, minOrderAmt, maxOrderAmt, startAt, endAt, isActive);
  }

  @Test
  @DisplayName("쿠폰 정책 정상 생성")
  void createCouponPolicySuccessfully() {
    // given

    // when
    CouponPolicy couponPolicy = createCouponPolicy();

    // then
    assertThat(couponPolicy)
        .isNotNull()
        .extracting(
            CouponPolicy::getCode,
            CouponPolicy::getName,
            CouponPolicy::getDiscountType,
            CouponPolicy::getDiscountValue,
            CouponPolicy::getMinOrderAmt,
            CouponPolicy::getMaxOrderAmt,
            CouponPolicy::getStartAt,
            CouponPolicy::getEndAt,
            CouponPolicy::isActive
        )
        .containsExactly(code, name, discountType, discountValue, minOrderAmt, maxOrderAmt, startAt,
            endAt, isActive);
  }


  @Test
  @DisplayName("FIXED 타입의 할인에 최소주문금액이 없을 경우 예외 발생")
  void throwExceptionForFixedDiscountType() {
    // given
    discountType = DISCOUNT_TYPE.FIXED;
    minOrderAmt = null;

    // when & then
    assertThatThrownBy(() -> createCouponPolicy())
        .isInstanceOf(CouponDiscountTypeException.class)
        .hasMessageContaining("고정 할인 유형에는 최소 주문 금액이 필수입니다.");
  }

  @Test
  @DisplayName("RATE 타입의 할인에 최대주문금액이 없을 경우 예외 발생")
  void throwExceptionForRateDiscountType() {
    // given
    discountType = DISCOUNT_TYPE.RATE;
    maxOrderAmt = null;

    // when & then
    assertThatThrownBy(() -> createCouponPolicy())
        .isInstanceOf(CouponDiscountTypeException.class) // 예외 타입 확인
        .hasMessageContaining("정률 할인 유형에는 최대 주문 금액이 필수입니다.");
  }

}