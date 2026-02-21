package com.live_commerce.coupon.adapter.out.persistence;

import com.live_commerce.coupon.domain.model.DISCOUNT_TYPE;
import com.live_commerce.coupon.presentation.dto.request.CouponPolicySearchResult;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CouponPolicyJpaRepository extends JpaRepository<CouponPolicyJpaEntity, String> {

    Optional<CouponPolicyJpaEntity> findByCodeAndDeletedStatusFalse(String code);

    List<CouponPolicyJpaEntity> findByDeletedStatusFalse();

    @Query("SELECT new com.live_commerce.coupon.presentation.dto.request.CouponPolicySearchResult(" +
            "c.code, c.name, c.discountType, c.discountValue, c.minOrderAmt, c.maxOrderAmt, c.startAt, c.endAt, c.isActive) " +
            "FROM CouponPolicyJpaEntity c " +
            "WHERE LOWER(c.name) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "AND (:discountType IS NULL OR c.discountType = :discountType) " +
            "AND c.endAt > CURRENT_TIMESTAMP " +
            "ORDER BY c.endAt ASC")
    Page<CouponPolicySearchResult> searchCouponPolicy(
            @Param("keyword") String keyword,
            @Param("discountType") DISCOUNT_TYPE discountType,
            Pageable pageable
    );
}
