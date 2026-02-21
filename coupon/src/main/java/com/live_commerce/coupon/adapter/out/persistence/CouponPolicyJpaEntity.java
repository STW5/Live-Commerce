package com.live_commerce.coupon.adapter.out.persistence;

import com.live_commerce.coupon.domain.model.DISCOUNT_TYPE;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 쿠폰 정책 JPA 엔티티 (Adapter Layer - Persistence 전담)
 * CouponPolicy 도메인 객체와 분리
 */
@Entity
@Getter
@Table(name = "p_coupon_policy")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CouponPolicyJpaEntity extends BaseJpaEntity {

    @Id
    @Column(nullable = false, updatable = false, unique = true)
    private String code;

    @Column(nullable = false, updatable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private DISCOUNT_TYPE discountType;

    @Column(nullable = false)
    private BigDecimal discountValue;

    @Column(nullable = false)
    private BigDecimal minOrderAmt;

    private BigDecimal maxOrderAmt;

    @Column(nullable = false)
    private LocalDateTime startAt;

    @Column(nullable = false)
    private LocalDateTime endAt;

    private boolean isActive;

    @Builder
    public CouponPolicyJpaEntity(String code, String name, DISCOUNT_TYPE discountType,
                                  BigDecimal discountValue, BigDecimal minOrderAmt,
                                  BigDecimal maxOrderAmt, LocalDateTime startAt,
                                  LocalDateTime endAt, boolean isActive) {
        this.code = code;
        this.name = name;
        this.discountType = discountType;
        this.discountValue = discountValue;
        this.minOrderAmt = minOrderAmt;
        this.maxOrderAmt = maxOrderAmt;
        this.startAt = startAt;
        this.endAt = endAt;
        this.isActive = isActive;
    }

    public void updateFields(String name, DISCOUNT_TYPE discountType, BigDecimal discountValue,
                              BigDecimal minOrderAmt, BigDecimal maxOrderAmt,
                              LocalDateTime startAt, LocalDateTime endAt, boolean isActive) {
        this.name = name;
        this.discountType = discountType;
        this.discountValue = discountValue;
        this.minOrderAmt = minOrderAmt;
        this.maxOrderAmt = maxOrderAmt;
        this.startAt = startAt;
        this.endAt = endAt;
        this.isActive = isActive;
    }
}
