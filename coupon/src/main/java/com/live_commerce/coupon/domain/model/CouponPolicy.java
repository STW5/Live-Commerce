package com.live_commerce.coupon.domain.model;

import com.live_commerce.coupon.domain.exception.CouponDiscountTypeException;
import com.live_commerce.coupon.domain.exception.CouponDomainException;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 쿠폰 정책 도메인 모델 (순수 Java - JPA/Presentation 의존 없음)
 *
 * [헥사고날 아키텍처 적용]
 * - @Entity, @Table 제거 → CouponPolicyJpaEntity로 분리
 * - presentation.dto.request.UpdateCouponPolicyRequest 의존 제거
 * - create() / reconstitute() 정적 팩토리 패턴 적용
 */
public class CouponPolicy {

    private String code;
    private String name;
    private DISCOUNT_TYPE discountType;
    private BigDecimal discountValue;
    private BigDecimal minOrderAmt;
    private BigDecimal maxOrderAmt;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private boolean isActive;

    // Audit 필드 (JPA 없이 직접 관리)
    private String createdBy;
    private LocalDateTime createdAt;
    private String updatedBy;
    private LocalDateTime updatedAt;
    private String deletedBy;
    private LocalDateTime deletedAt;
    private Boolean deletedStatus = false;

    private CouponPolicy() {}

    /**
     * 신규 쿠폰 정책 생성 팩토리
     */
    public static CouponPolicy create(String code, String name, DISCOUNT_TYPE discountType,
                                      BigDecimal discountValue, BigDecimal minOrderAmt,
                                      BigDecimal maxOrderAmt, LocalDateTime startAt,
                                      LocalDateTime endAt, boolean isActive) {
        if (code == null || name == null || discountType == null || discountValue == null
                || startAt == null || endAt == null) {
            throw new CouponDomainException("CouponPolicy의 필수 값이 null일 수 없습니다.");
        }
        CouponPolicy policy = new CouponPolicy();
        policy.code = code;
        policy.name = name;
        policy.discountType = discountType;
        policy.discountValue = discountValue;
        policy.minOrderAmt = minOrderAmt;
        policy.maxOrderAmt = maxOrderAmt;
        policy.startAt = startAt;
        policy.endAt = endAt;
        policy.isActive = isActive;
        policy.deletedStatus = false;
        policy.validateDiscountType();
        return policy;
    }

    /**
     * 영속성 계층에서 도메인 객체 복원 팩토리
     */
    public static CouponPolicy reconstitute(String code, String name, DISCOUNT_TYPE discountType,
                                            BigDecimal discountValue, BigDecimal minOrderAmt,
                                            BigDecimal maxOrderAmt, LocalDateTime startAt,
                                            LocalDateTime endAt, boolean isActive,
                                            String createdBy, LocalDateTime createdAt,
                                            String updatedBy, LocalDateTime updatedAt,
                                            String deletedBy, LocalDateTime deletedAt,
                                            Boolean deletedStatus) {
        CouponPolicy policy = new CouponPolicy();
        policy.code = code;
        policy.name = name;
        policy.discountType = discountType;
        policy.discountValue = discountValue;
        policy.minOrderAmt = minOrderAmt;
        policy.maxOrderAmt = maxOrderAmt;
        policy.startAt = startAt;
        policy.endAt = endAt;
        policy.isActive = isActive;
        policy.createdBy = createdBy;
        policy.createdAt = createdAt;
        policy.updatedBy = updatedBy;
        policy.updatedAt = updatedAt;
        policy.deletedBy = deletedBy;
        policy.deletedAt = deletedAt;
        policy.deletedStatus = deletedStatus != null ? deletedStatus : false;
        return policy;
    }

    /**
     * 쿠폰 정책 수정 (presentation 의존 없이 파라미터로 직접 받음)
     */
    public void update(String name, DISCOUNT_TYPE discountType, BigDecimal discountValue,
                       BigDecimal minOrderAmt, BigDecimal maxOrderAmt,
                       LocalDateTime startAt, LocalDateTime endAt, boolean isActive) {
        if (name == null || discountType == null || discountValue == null
                || startAt == null || endAt == null) {
            throw new CouponDomainException("CouponPolicy의 필수 값이 null일 수 없습니다.");
        }
        this.name = name;
        this.discountType = discountType;
        this.discountValue = discountValue;
        this.minOrderAmt = minOrderAmt;
        this.maxOrderAmt = maxOrderAmt;
        this.startAt = startAt;
        this.endAt = endAt;
        this.isActive = isActive;
        validateDiscountType();
    }

    public void validateDiscountType() {
        if (this.discountType == DISCOUNT_TYPE.FIXED && this.minOrderAmt == null) {
            CouponDiscountTypeException.forFixedDiscount();
        }
        if (this.discountType == DISCOUNT_TYPE.RATE && this.maxOrderAmt == null) {
            CouponDiscountTypeException.forRateDiscount();
        }
    }

    public void markAsDeleted(String deletedBy) {
        if (this.deletedStatus) {
            throw new IllegalStateException("Already deleted");
        }
        this.deletedStatus = true;
        this.deletedBy = deletedBy;
        this.deletedAt = LocalDateTime.now();
    }

    // Getters
    public String getCode() { return code; }
    public String getName() { return name; }
    public DISCOUNT_TYPE getDiscountType() { return discountType; }
    public BigDecimal getDiscountValue() { return discountValue; }
    public BigDecimal getMinOrderAmt() { return minOrderAmt; }
    public BigDecimal getMaxOrderAmt() { return maxOrderAmt; }
    public LocalDateTime getStartAt() { return startAt; }
    public LocalDateTime getEndAt() { return endAt; }
    public boolean isActive() { return isActive; }
    public String getCreatedBy() { return createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public String getUpdatedBy() { return updatedBy; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public String getDeletedBy() { return deletedBy; }
    public LocalDateTime getDeletedAt() { return deletedAt; }
    public Boolean getDeletedStatus() { return deletedStatus; }
}
