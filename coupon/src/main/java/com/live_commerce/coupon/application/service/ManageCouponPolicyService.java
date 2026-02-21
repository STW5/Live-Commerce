package com.live_commerce.coupon.application.service;

import com.live_commerce.coupon.application.dto.command.CreateCouponPolicyCommand;
import com.live_commerce.coupon.application.dto.result.CouponPolicyResult;
import com.live_commerce.coupon.domain.exception.CouponPolicyException;
import com.live_commerce.coupon.domain.model.CouponPolicy;
import com.live_commerce.coupon.domain.port.in.ManageCouponPolicyUseCase;
import com.live_commerce.coupon.domain.port.out.CouponPolicyRepositoryPort;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 쿠폰 정책 관리 유즈케이스 구현체
 *
 * [헥사고날 아키텍처 적용]
 * - ManageCouponPolicyUseCase 인터페이스 구현 (Inbound Port)
 * - Port 인터페이스만 주입
 * - RequestUserDetails 의존 제거 (권한 검증은 Presentation/Controller 레이어 책임)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ManageCouponPolicyService implements ManageCouponPolicyUseCase {

    private final CouponPolicyRepositoryPort couponPolicyRepositoryPort;

    @Override
    @Transactional
    public CouponPolicyResult createCouponPolicy(CreateCouponPolicyCommand command) {
        CouponPolicy policy = CouponPolicy.create(
                command.code(), command.name(), command.discountType(),
                command.discountValue(), command.minOrderAmt(), command.maxOrderAmt(),
                command.startAt(), command.endAt(), command.isActive()
        );
        CouponPolicy saved = couponPolicyRepositoryPort.save(policy);
        log.info("[ManageCouponPolicyService] 쿠폰 정책 생성: code={}", command.code());
        return CouponPolicyResult.from(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public CouponPolicyResult getCouponPolicy(String code) {
        CouponPolicy policy = couponPolicyRepositoryPort.findActiveByCode(code)
                .orElseThrow(() -> {
                    CouponPolicyException.forCouponPolicyNotFound();
                    return null;
                });
        return CouponPolicyResult.from(policy);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CouponPolicyResult> getCouponPolicies() {
        return couponPolicyRepositoryPort.findAllActive().stream()
                .map(CouponPolicyResult::from)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void updateCouponPolicy(String code, CreateCouponPolicyCommand command) {
        CouponPolicy policy = couponPolicyRepositoryPort.findActiveByCode(code)
                .orElseThrow(() -> {
                    CouponPolicyException.forCouponPolicyNotFound();
                    return null;
                });
        policy.update(command.name(), command.discountType(), command.discountValue(),
                command.minOrderAmt(), command.maxOrderAmt(),
                command.startAt(), command.endAt(), command.isActive());
        couponPolicyRepositoryPort.save(policy);
        log.info("[ManageCouponPolicyService] 쿠폰 정책 수정: code={}", code);
    }

    @Override
    @Transactional
    public void deleteCouponPolicy(String code) {
        CouponPolicy policy = couponPolicyRepositoryPort.findByCode(code)
                .orElseThrow(() -> {
                    CouponPolicyException.forCouponPolicyNotFound();
                    return null;
                });
        policy.markAsDeleted(code);
        couponPolicyRepositoryPort.save(policy);
        log.info("[ManageCouponPolicyService] 쿠폰 정책 삭제: code={}", code);
    }
}
