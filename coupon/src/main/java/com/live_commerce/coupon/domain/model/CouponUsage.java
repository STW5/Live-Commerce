package com.live_commerce.coupon.domain.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.*;

/**
 * @deprecated 사용되지 않는 레거시 JPA 엔티티. 헥사고날 아키텍처 전환 시 도메인 레이어에서 제거 예정.
 */
@Deprecated(since = "hexagonal-ddd-coupon", forRemoval = true)
@Entity
@Table(name = "p_coupon_usage")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CouponUsage extends BaseEntity {

  @Id
  @Column(nullable = false, updatable = false)
  private UUID id;

  @Column(nullable = false)
  private UUID orderId;

  @Column(nullable = false)
  private String couponCode;

  @Column(nullable = false)
  private BigDecimal amount;

  private boolean isUsed = false;

  private LocalDateTime usedAt;

}