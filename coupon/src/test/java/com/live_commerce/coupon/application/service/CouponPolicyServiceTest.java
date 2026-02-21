package com.live_commerce.coupon.application.service;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.live_commerce.coupon.adapter.out.persistence.CouponPolicyJpaRepository;
import com.live_commerce.coupon.application.exception.CouponPolicyExceptionCode;
import com.live_commerce.coupon.application.validation.CouponPolicyValidator;
import com.live_commerce.coupon.domain.exception.CouponPolicyException;
import com.live_commerce.coupon.domain.model.CouponPolicy;
import com.live_commerce.coupon.domain.model.DISCOUNT_TYPE;
import com.live_commerce.coupon.domain.port.out.CouponPolicyRepositoryPort;
import com.live_commerce.coupon.infrastructure.security.RequestUserDetails;
import com.live_commerce.coupon.presentation.dto.request.CreateCouponPolicyRequest;
import com.live_commerce.coupon.presentation.dto.request.UpdateCouponPolicyRequest;
import com.live_commerce.coupon.presentation.dto.response.ReadCouponPolicyResponse;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class CouponPolicyServiceTest {

  @Mock
  private CouponPolicyRepositoryPort couponPolicyRepositoryPort;

  @Mock
  private CouponPolicyJpaRepository couponPolicyJpaRepository;

  @Mock
  private CouponPolicyValidator couponPolicyValidator;

  @InjectMocks
  private CouponPolicyService couponPolicyService;

  @Mock
  private RequestUserDetails userDetails;

  private CreateCouponPolicyRequest request;

  private CouponPolicy couponPolicy;

  @BeforeEach
  void setUp() {
    String code = "SUMMER_SALE_100";
    request = new CreateCouponPolicyRequest(
        code,
        "테스트 쿠폰",
        DISCOUNT_TYPE.FIXED,
        BigDecimal.valueOf(100),
        BigDecimal.valueOf(500),
        BigDecimal.valueOf(1000),
        LocalDateTime.now().plusDays(1),
        LocalDateTime.now().plusDays(30),
        true
    );

    couponPolicy = CouponPolicy.create(
        code, "테스트 쿠폰", DISCOUNT_TYPE.FIXED,
        BigDecimal.valueOf(100), BigDecimal.valueOf(500), BigDecimal.valueOf(1000),
        LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(30), true
    );
  }

  @Test
  @DisplayName("쿠폰 시작일이 종료일보다 클 경우 예외 발생")
  void throwExceptionForInvalidDateRange() {
    // given
    request = new CreateCouponPolicyRequest(
        request.code(),
        request.name(),
        request.discountType(),
        request.discountValue(),
        request.minOrderAmt(),
        request.maxOrderAmt(),
        LocalDateTime.now().plusDays(10),
        LocalDateTime.now().plusDays(5),
        request.isActive()
    );

    // when & then
    doThrow(new CouponPolicyException(CouponPolicyExceptionCode.INVALID_DATE_RANGE))
        .when(couponPolicyValidator).validateForCreatePolicy(
            any(CreateCouponPolicyRequest.class));

    assertThatThrownBy(() -> couponPolicyService.createCouponPolicy(request, userDetails))
        .isInstanceOf(CouponPolicyException.class)
        .hasMessageContaining("시작일은 종료일보다 이전이어야 합니다.");
  }

  @Test
  @DisplayName("고정 할인 금액이 최대 주문 금액보다 클 경우 예외 발생")
  void throwExceptionForDiscountGreaterThanMaxOrderAmt() {
    // given
    request = new CreateCouponPolicyRequest(
        request.code(),
        request.name(),
        request.discountType(),
        BigDecimal.valueOf(2000),
        request.minOrderAmt(),
        BigDecimal.valueOf(1000),
        request.startAt(),
        request.endAt(),
        request.isActive()
    );
    // when & then
    doThrow(new CouponPolicyException(CouponPolicyExceptionCode.DISCOUNT_GREATER_THAN_MAX_ORDER_AMOUNT))
        .when(couponPolicyValidator).validateForCreatePolicy(any(CreateCouponPolicyRequest.class));

    assertThatThrownBy(() -> couponPolicyService.createCouponPolicy(request, userDetails))
        .isInstanceOf(CouponPolicyException.class)
        .hasMessageContaining("할인 금액이 최대 주문 금액을 초과할 수 없습니다.");
  }

  @Test
  @DisplayName("정률 할인 금액이 100을 초과할 경우 예외 발생")
  void throwExceptionForRateDiscountGreaterThan100() {
    request = new CreateCouponPolicyRequest(
        request.code(),
        request.name(),
        DISCOUNT_TYPE.RATE,
        BigDecimal.valueOf(110),
        request.minOrderAmt(),
        request.maxOrderAmt(),
        request.startAt(),
        request.endAt(),
        request.isActive()
    );
    // when & then
    doThrow(new CouponPolicyException(CouponPolicyExceptionCode.DISCOUNT_GREATER_THAN_100))
        .when(couponPolicyValidator).validateForCreatePolicy(any(CreateCouponPolicyRequest.class));

    assertThatThrownBy(() -> couponPolicyService.createCouponPolicy(request, userDetails))
        .isInstanceOf(CouponPolicyException.class)
        .hasMessageContaining("정률 할인 비율은 100을 넘을 수 없습니다.");
  }

  @Test
  @DisplayName("존재하는 쿠폰 정책 조회 성공")
  void getCouponPolicySuccess() {
    // given
    String validCouponId = couponPolicy.getCode();

    // when
    when(couponPolicyRepositoryPort.findActiveByCode(validCouponId)).thenReturn(
        Optional.of(couponPolicy));

    ReadCouponPolicyResponse response = couponPolicyService.getCouponPolicy(validCouponId, userDetails);

    // then
    assertThat(response).isNotNull();
    assertThat(response.code()).isEqualTo(validCouponId);
    verify(couponPolicyRepositoryPort, times(1)).findActiveByCode(validCouponId);
  }

  @Test
  @DisplayName("존재하지 않는 쿠폰 정책 조회 시 예외 발생")
  void getCouponPolicyNotFound() {
    // given
    String code = "WINTER_SALE_100";

    // when
    when(couponPolicyRepositoryPort.findActiveByCode(code)).thenReturn(Optional.empty());

    // then
    assertThatThrownBy(() -> couponPolicyService.getCouponPolicy(code, userDetails))
        .isInstanceOf(CouponPolicyException.class)
        .hasMessageContaining("쿠폰 정책이 없거나 모두 삭제되었습니다.");

    verify(couponPolicyRepositoryPort, times(1)).findActiveByCode(code);
  }

  @Test
  @DisplayName("쿠폰 정책 삭제 시 소프트 delete 적용 확인")
  void deleteCouponPolicySoftDelete() {
    // given
    String validCouponId = couponPolicy.getCode();

    // when
    when(couponPolicyRepositoryPort.findByCode(validCouponId)).thenReturn(Optional.of(couponPolicy));
    when(couponPolicyRepositoryPort.save(any(CouponPolicy.class))).thenReturn(couponPolicy);

    couponPolicyService.deleteCouponPolicy(validCouponId, userDetails);

    // then
    assertThat(couponPolicy.getDeletedStatus()).isTrue();
    assertThat(couponPolicy.getDeletedBy()).isNotNull();
    assertThat(couponPolicy.getDeletedAt()).isNotNull();

    when(couponPolicyRepositoryPort.findActiveByCode(validCouponId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> couponPolicyService.getCouponPolicy(validCouponId, userDetails))
        .isInstanceOf(CouponPolicyException.class)
        .hasMessageContaining("쿠폰 정책이 없거나 모두 삭제되었습니다.");

    verify(couponPolicyRepositoryPort, times(1)).save(any(CouponPolicy.class));
  }

  @Test
  @DisplayName("쿠폰 정책 수정 성공")
  void updateCouponPolicySuccess() {
    // given
    String validCouponCode = couponPolicy.getCode();

    UpdateCouponPolicyRequest updateRequest = new UpdateCouponPolicyRequest(
        "수정된 쿠폰",
        DISCOUNT_TYPE.FIXED,
        BigDecimal.valueOf(200),
        BigDecimal.valueOf(500),
        BigDecimal.valueOf(1000),
        LocalDateTime.now().plusDays(2),
        LocalDateTime.now().plusDays(60),
        true
    );

    // when
    when(couponPolicyRepositoryPort.findActiveByCode(validCouponCode)).thenReturn(Optional.of(couponPolicy));
    when(couponPolicyRepositoryPort.save(any(CouponPolicy.class))).thenReturn(couponPolicy);

    couponPolicyService.updateCouponPolicy(validCouponCode, updateRequest, userDetails);

    // then
    assertThat(couponPolicy.getName()).isEqualTo("수정된 쿠폰");
    assertThat(couponPolicy.getDiscountValue()).isEqualTo(BigDecimal.valueOf(200));
    assertThat(couponPolicy.getStartAt()).isEqualTo(updateRequest.startAt());
    assertThat(couponPolicy.getEndAt()).isEqualTo(updateRequest.endAt());

    verify(couponPolicyRepositoryPort, times(1)).findActiveByCode(validCouponCode);
    verify(couponPolicyRepositoryPort, times(1)).save(any(CouponPolicy.class));
  }
}
