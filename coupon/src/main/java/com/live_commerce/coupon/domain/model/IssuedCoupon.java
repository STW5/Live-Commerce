package com.live_commerce.coupon.domain.model;

import com.live_commerce.coupon.infrastructure.security.RequestUserDetails;
import com.live_commerce.coupon.presentation.dto.request.IssuedCouponRequest;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.*;

@Entity
@Getter
@Table(name = "p_issued_coupon")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IssuedCoupon {

  @Id
  @GeneratedValue(strategy = GenerationType.AUTO)
  private UUID id;

  @Column(nullable = false)
  private UUID userId;

  @Column(nullable = false, updatable = false)
  private String couponCode;

  private Boolean isUsed;

  private LocalDateTime usedAt;

  @Column(nullable = false)
  private LocalDateTime expiresAt;

  @Builder
  public IssuedCoupon(UUID id, UUID userId, String couponCode, boolean isUsed, LocalDateTime usedAt,
      LocalDateTime expiresAt) {
    this.id = id;
    this.userId = userId;
    this.couponCode = couponCode;
    this.isUsed = isUsed;
    this.usedAt = usedAt;
    this.expiresAt = expiresAt;
  }

  public static IssuedCoupon from(IssuedCouponRequest request,
      Optional<CouponPolicy> couponPolicy, RequestUserDetails userDetails) {
    return IssuedCoupon.builder()
//        .id(UUID.randomUUID())
        .userId(userDetails.getUserId())
        .couponCode(request.couponCode())
        .isUsed(false)
        .usedAt(null)
        .expiresAt(couponPolicy.get().getEndAt())
        .build();
  }

  public static IssuedCoupon from(IssuedCouponRequest request,
      Optional<CouponPolicy> couponPolicy, UUID userId) {
    return IssuedCoupon.builder()
        //        .id(UUID.randomUUID())
        .userId(userId)
        .couponCode(request.couponCode())
        .isUsed(false)
        .usedAt(null)
        .expiresAt(couponPolicy.get().getEndAt())
        .build();
  }


  public void useCoupon() {
    if (this.isUsed) {
      throw new IllegalStateException("This coupon has already been used");
    }
    this.isUsed = true;
    this.usedAt = LocalDateTime.now();
  }

  /**
   * 쿠폰 사용 취소 (보상 트랜잭션)
   * 주문 실패 시 사용된 쿠폰을 복구합니다.
   */
  public void restoreCoupon() {
    if (!this.isUsed) {
      throw new IllegalStateException("This coupon has not been used yet");
    }
    this.isUsed = false;
    this.usedAt = null;
  }

}
