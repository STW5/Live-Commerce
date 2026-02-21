package com.live_commerce.coupon.application.service;

import com.live_commerce.coupon.application.dto.command.UseCouponCommand;
import com.live_commerce.coupon.application.dto.result.IssuedCouponResult;
import com.live_commerce.coupon.domain.exception.IssuedCouponException;
import com.live_commerce.coupon.domain.model.IssuedCoupon;
import com.live_commerce.coupon.domain.port.in.UseCouponUseCase;
import com.live_commerce.coupon.domain.port.out.CouponEventPublisher;
import com.live_commerce.coupon.domain.port.out.IssuedCouponRepositoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 쿠폰 사용 유즈케이스 구현체
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UseCouponService implements UseCouponUseCase {

    private final IssuedCouponRepositoryPort issuedCouponRepositoryPort;
    private final CouponEventPublisher couponEventPublisher;

    @Override
    @Transactional
    public IssuedCouponResult useCoupon(UseCouponCommand command) {
        IssuedCoupon coupon = issuedCouponRepositoryPort
                .findByIdAndUserIdAndNotUsed(command.couponId(), command.userId())
                .orElseThrow(() -> {
                    IssuedCouponException.issuedCouponNotFound();
                    return null;
                });

        coupon.useCoupon();
        IssuedCoupon saved = issuedCouponRepositoryPort.save(coupon);
        couponEventPublisher.publishCouponUsedEvent(saved.getId(), saved.getUserId());
        log.info("[UseCouponService] 쿠폰 사용 완료: couponId={}, userId={}", command.couponId(), command.userId());
        return IssuedCouponResult.from(saved);
    }
}
