package com.live_commerce.coupon.application.service;

import com.live_commerce.coupon.application.dto.command.IssueCouponCommand;
import com.live_commerce.coupon.application.dto.result.IssuedCouponResult;
import com.live_commerce.coupon.domain.exception.IssuedCouponException;
import com.live_commerce.coupon.domain.model.CouponPolicy;
import com.live_commerce.coupon.domain.model.DISCOUNT_TYPE;
import com.live_commerce.coupon.domain.model.IssuedCoupon;
import com.live_commerce.coupon.domain.port.in.IssueCouponUseCase;
import com.live_commerce.coupon.domain.port.out.CouponPolicyRepositoryPort;
import com.live_commerce.coupon.domain.port.out.IssuedCouponRepositoryPort;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 쿠폰 발급 유즈케이스 구현체
 *
 * [헥사고날 아키텍처 적용]
 * - IssueCouponUseCase 인터페이스 구현 (Inbound Port)
 * - Port 인터페이스만 주입 (JPA Repository, Feign 클래스 import 없음)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IssueCouponService implements IssueCouponUseCase {

    private final CouponPolicyRepositoryPort couponPolicyRepositoryPort;
    private final IssuedCouponRepositoryPort issuedCouponRepositoryPort;

    @Override
    @Transactional
    public IssuedCouponResult issueCoupon(IssueCouponCommand command) {
        CouponPolicy policy = couponPolicyRepositoryPort
                .findActiveByCode(command.couponCode())
                .orElseThrow(() -> {
                    IssuedCouponException.couponPolicyNotFound();
                    return null;
                });

        IssuedCoupon issued = IssuedCoupon.issue(command.userId(), policy.getCode(), policy.getEndAt());
        IssuedCoupon saved = issuedCouponRepositoryPort.save(issued);
        log.info("[IssueCouponService] 쿠폰 발급 완료: userId={}, couponCode={}", command.userId(), command.couponCode());
        return IssuedCouponResult.from(saved);
    }

    @Override
    @Transactional
    public IssuedCouponResult issueFirstJoinCoupon(UUID userId) {
        String couponCode = "FIRST_COUPON";
        // FIRST_COUPON 정책이 없으면 기본 정책 생성
        CouponPolicy policy = couponPolicyRepositoryPort
                .findActiveByCode(couponCode)
                .orElseGet(() -> createFirstCouponPolicy(couponCode));

        IssuedCoupon issued = IssuedCoupon.issue(userId, policy.getCode(), policy.getEndAt());
        IssuedCoupon saved = issuedCouponRepositoryPort.save(issued);
        log.info("[IssueCouponService] 회원가입 쿠폰 발급 완료: userId={}", userId);
        return IssuedCouponResult.from(saved);
    }

    private CouponPolicy createFirstCouponPolicy(String couponCode) {
        CouponPolicy policy = CouponPolicy.create(
                couponCode,
                "First Coupon for Signup",
                DISCOUNT_TYPE.FIXED,
                BigDecimal.valueOf(15000),
                BigDecimal.valueOf(0),
                BigDecimal.valueOf(50000),
                LocalDateTime.now(),
                LocalDateTime.now().plusYears(1),
                true
        );
        return couponPolicyRepositoryPort.save(policy);
    }
}
