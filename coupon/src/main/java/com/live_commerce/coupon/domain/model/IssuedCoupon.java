package com.live_commerce.coupon.domain.model;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 발급된 쿠폰 도메인 모델 (순수 Java - JPA/Presentation/Infrastructure 의존 없음)
 *
 * [헥사고날 아키텍처 적용]
 * - @Entity, @Table 제거 → IssuedCouponJpaEntity로 분리
 * - infrastructure.security.RequestUserDetails 의존 제거
 * - presentation.dto.request.IssuedCouponRequest 의존 제거
 * - issue() / reconstitute() 정적 팩토리 패턴 적용
 */
public class IssuedCoupon {

    private UUID id;
    private UUID userId;
    private String couponCode;
    private Boolean isUsed;
    private LocalDateTime usedAt;
    private LocalDateTime expiresAt;

    private IssuedCoupon() {}

    /**
     * 쿠폰 신규 발급 팩토리
     */
    public static IssuedCoupon issue(UUID userId, String couponCode, LocalDateTime expiresAt) {
        IssuedCoupon coupon = new IssuedCoupon();
        coupon.userId = userId;
        coupon.couponCode = couponCode;
        coupon.isUsed = false;
        coupon.usedAt = null;
        coupon.expiresAt = expiresAt;
        return coupon;
    }

    /**
     * 영속성 계층에서 도메인 객체 복원 팩토리
     */
    public static IssuedCoupon reconstitute(UUID id, UUID userId, String couponCode,
                                            Boolean isUsed, LocalDateTime usedAt,
                                            LocalDateTime expiresAt) {
        IssuedCoupon coupon = new IssuedCoupon();
        coupon.id = id;
        coupon.userId = userId;
        coupon.couponCode = couponCode;
        coupon.isUsed = isUsed != null ? isUsed : false;
        coupon.usedAt = usedAt;
        coupon.expiresAt = expiresAt;
        return coupon;
    }

    /**
     * 쿠폰 사용
     */
    public void useCoupon() {
        if (Boolean.TRUE.equals(this.isUsed)) {
            throw new IllegalStateException("This coupon has already been used");
        }
        this.isUsed = true;
        this.usedAt = LocalDateTime.now();
    }

    /**
     * 쿠폰 사용 취소 (보상 트랜잭션)
     */
    public void restoreCoupon() {
        if (!Boolean.TRUE.equals(this.isUsed)) {
            throw new IllegalStateException("This coupon has not been used yet");
        }
        this.isUsed = false;
        this.usedAt = null;
    }

    // Getters
    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getCouponCode() { return couponCode; }
    public Boolean getIsUsed() { return isUsed; }
    public boolean isUsed() { return Boolean.TRUE.equals(isUsed); }
    public LocalDateTime getUsedAt() { return usedAt; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
}
