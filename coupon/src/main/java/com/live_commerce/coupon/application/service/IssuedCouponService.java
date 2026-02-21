package com.live_commerce.coupon.application.service;

import com.live_commerce.coupon.domain.exception.IssuedCouponException;
import com.live_commerce.coupon.domain.model.CouponPolicy;
import com.live_commerce.coupon.domain.model.DISCOUNT_TYPE;
import com.live_commerce.coupon.domain.model.IssuedCoupon;
import com.live_commerce.coupon.domain.port.out.CouponPolicyRepositoryPort;
import com.live_commerce.coupon.domain.port.out.IssuedCouponRepositoryPort;
import com.live_commerce.coupon.domain.port.out.OrderQueryPort;
import com.live_commerce.coupon.infrastructure.security.RequestUserDetails;
import com.live_commerce.coupon.presentation.dto.request.IssuedCouponRequest;
import com.live_commerce.coupon.presentation.dto.response.FirstJoinCouponResponse;
import com.live_commerce.coupon.presentation.dto.response.GetIssuedCouponResponse;
import com.live_commerce.coupon.presentation.dto.response.IssuedCouponListResponse;
import com.live_commerce.coupon.presentation.dto.response.UsedIssuedCouponResponse;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 발급 쿠폰 서비스 (레거시)
 *
 * @deprecated 헥사고날 아키텍처 전환으로 {@link IssueCouponService}, {@link UseCouponService},
 *             {@link RestoreCouponService} (각 UseCase 구현체)로 대체 예정.
 *             현재는 기존 Controller에서 참조하므로 유지.
 */
@Deprecated(since = "hexagonal-ddd-coupon", forRemoval = true)
@Slf4j
@Service
@Transactional
@AllArgsConstructor
public class IssuedCouponService {

    private final IssuedCouponRepositoryPort issuedCouponRepositoryPort;
    private final CouponPolicyRepositoryPort couponPolicyRepositoryPort;
    private final OrderQueryPort orderQueryPort;

    public IssuedCoupon issueCoupon(IssuedCouponRequest request, RequestUserDetails userDetails) {
        CouponPolicy policy = couponPolicyRepositoryPort
                .findActiveByCode(request.couponCode())
                .orElseThrow(() -> {
                    IssuedCouponException.couponPolicyNotFound();
                    return null;
                });
        IssuedCoupon issued = IssuedCoupon.issue(userDetails.getUserId(), policy.getCode(), policy.getEndAt());
        return issuedCouponRepositoryPort.save(issued);
    }

    public IssuedCoupon issueFirstCoupon(IssuedCouponRequest request, UUID userId) {
        CouponPolicy policy = couponPolicyRepositoryPort
                .findActiveByCode(request.couponCode())
                .orElseThrow(() -> {
                    IssuedCouponException.couponPolicyNotFound();
                    return null;
                });
        IssuedCoupon issued = IssuedCoupon.issue(userId, policy.getCode(), policy.getEndAt());
        return issuedCouponRepositoryPort.save(issued);
    }

    public IssuedCoupon useCoupon(UUID couponId, RequestUserDetails userDetails) {
        IssuedCoupon coupon = issuedCouponRepositoryPort
                .findByIdAndUserIdAndNotUsed(couponId, userDetails.getUserId())
                .orElseThrow(() -> {
                    IssuedCouponException.issuedCouponNotFound();
                    return null;
                });
        if (coupon.isUsed()) {
            IssuedCouponException.alreadyUsedCoupon();
        }
        coupon.useCoupon();
        return issuedCouponRepositoryPort.save(coupon);
    }

    @Transactional(readOnly = true)
    public GetIssuedCouponResponse getIssuedCoupon(UUID couponId, RequestUserDetails userDetails) {
        IssuedCoupon coupon = issuedCouponRepositoryPort
                .findByIdAndUserIdAndNotUsed(couponId, userDetails.getUserId())
                .orElseThrow(() -> {
                    IssuedCouponException.issuedCouponNotFound();
                    return null;
                });
        return GetIssuedCouponResponse.from(coupon);
    }

    @Transactional(readOnly = true)
    public IssuedCouponListResponse getIssuedCoupons(RequestUserDetails userDetails) {
        List<IssuedCoupon> coupons = issuedCouponRepositoryPort.findByUserId(userDetails.getUserId());
        return IssuedCouponListResponse.from(coupons);
    }

    public FirstJoinCouponResponse issueFirstCoupon(UUID userId) {
        String couponCode = "FIRST_COUPON";
        CouponPolicy policy = couponPolicyRepositoryPort
                .findActiveByCode(couponCode)
                .orElseGet(() -> createFirstCouponPolicy(couponCode));
        IssuedCoupon issued = IssuedCoupon.issue(userId, policy.getCode(), policy.getEndAt());
        IssuedCoupon saved = issuedCouponRepositoryPort.save(issued);
        return FirstJoinCouponResponse.from(saved);
    }

    private CouponPolicy createFirstCouponPolicy(String couponCode) {
        CouponPolicy policy = CouponPolicy.create(
                couponCode, "First Coupon for Signup", DISCOUNT_TYPE.FIXED,
                BigDecimal.valueOf(15000), BigDecimal.valueOf(0), BigDecimal.valueOf(50000),
                LocalDateTime.now(), LocalDateTime.now().plusYears(1), true
        );
        return couponPolicyRepositoryPort.save(policy);
    }

    public void issueFirstCouponDirectly(UUID userId) {
        issueFirstCoupon(userId);
    }

    public UsedIssuedCouponResponse useCouponAndPublishEvent(UUID couponId, RequestUserDetails userDetails) {
        IssuedCoupon issued = useCoupon(couponId, userDetails);
        return UsedIssuedCouponResponse.from(issued);
    }

    public void handleCouponUsedEvent(UUID couponId, UUID userId) {
        log.info("✅ 쿠폰 사용 후처리 시작: couponId={}, userId={}", couponId, userId);
        IssuedCoupon coupon = issuedCouponRepositoryPort
                .findByIdAndUserIdAndNotUsed(couponId, userId)
                .orElseThrow(() -> {
                    IssuedCouponException.issuedCouponNotFound();
                    return null;
                });
        if (coupon.isUsed()) {
            IssuedCouponException.alreadyUsedCoupon();
        }
        coupon.useCoupon();
        issuedCouponRepositoryPort.save(coupon);
    }

    public void handleOrderFailedEvent(UUID orderId) {
        log.info("[보상 트랜잭션] 쿠폰 복구 시작 - orderId: {}", orderId);
        OrderQueryPort.OrderInfo order = orderQueryPort.getOrder(orderId);
        if (order.couponId() == null) {
            log.info("[보상 트랜잭션] 쿠폰을 사용하지 않은 주문 - orderId: {}", orderId);
            return;
        }
        IssuedCoupon coupon = issuedCouponRepositoryPort
                .findByIdAndUserId(order.couponId(), order.userId())
                .orElseThrow(() -> {
                    IssuedCouponException.issuedCouponNotFound();
                    return null;
                });
        if (!coupon.isUsed()) {
            log.info("[보상 트랜잭션] 이미 미사용 상태인 쿠폰 - couponId: {}", order.couponId());
            return;
        }
        coupon.restoreCoupon();
        issuedCouponRepositoryPort.save(coupon);
        log.info("[보상 트랜잭션] 쿠폰 복구 완료 - couponId: {}, orderId: {}", order.couponId(), orderId);
    }
}
