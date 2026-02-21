package com.live_commerce.coupon.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 발급된 쿠폰 JPA 엔티티 (Adapter Layer - Persistence 전담)
 * IssuedCoupon 도메인 객체와 분리
 */
@Entity
@Getter
@Table(name = "p_issued_coupon")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IssuedCouponJpaEntity {

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
    public IssuedCouponJpaEntity(UUID id, UUID userId, String couponCode,
                                  Boolean isUsed, LocalDateTime usedAt, LocalDateTime expiresAt) {
        this.id = id;
        this.userId = userId;
        this.couponCode = couponCode;
        this.isUsed = isUsed;
        this.usedAt = usedAt;
        this.expiresAt = expiresAt;
    }

    public void applyUsed(Boolean isUsed, LocalDateTime usedAt) {
        this.isUsed = isUsed;
        this.usedAt = usedAt;
    }
}
