package com.live_commerce.coupon.domain.port.out;

import com.live_commerce.coupon.domain.model.CouponPolicy;
import java.util.List;
import java.util.Optional;

/**
 * 쿠폰 정책 영속성 포트 (Outbound)
 * - Domain Layer에 위치: 순수 인터페이스
 * - JPA, Spring Data 등 인프라 의존 없음
 * - 구현체: adapter/out/persistence/CouponPolicyPersistenceAdapter
 */
public interface CouponPolicyRepositoryPort {
    CouponPolicy save(CouponPolicy couponPolicy);
    Optional<CouponPolicy> findByCode(String code);
    Optional<CouponPolicy> findActiveByCode(String code);
    List<CouponPolicy> findAllActive();
}
