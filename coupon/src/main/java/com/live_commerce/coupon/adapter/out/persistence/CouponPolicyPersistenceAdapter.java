package com.live_commerce.coupon.adapter.out.persistence;

import com.live_commerce.coupon.domain.model.CouponPolicy;
import com.live_commerce.coupon.domain.port.out.CouponPolicyRepositoryPort;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 쿠폰 정책 영속성 어댑터 (CouponPolicyRepositoryPort 구현체)
 */
@Component
@RequiredArgsConstructor
public class CouponPolicyPersistenceAdapter implements CouponPolicyRepositoryPort {

    private final CouponPolicyJpaRepository jpaRepository;
    private final CouponPolicyMapper mapper;

    @Override
    public CouponPolicy save(CouponPolicy couponPolicy) {
        CouponPolicyJpaEntity entity = mapper.toJpaEntity(couponPolicy);
        CouponPolicyJpaEntity saved = jpaRepository.save(entity);
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<CouponPolicy> findByCode(String code) {
        return jpaRepository.findById(code).map(mapper::toDomain);
    }

    @Override
    public Optional<CouponPolicy> findActiveByCode(String code) {
        return jpaRepository.findByCodeAndDeletedStatusFalse(code).map(mapper::toDomain);
    }

    @Override
    public List<CouponPolicy> findAllActive() {
        return jpaRepository.findByDeletedStatusFalse().stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }
}
