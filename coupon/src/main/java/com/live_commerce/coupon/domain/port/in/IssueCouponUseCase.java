package com.live_commerce.coupon.domain.port.in;

import com.live_commerce.coupon.application.dto.command.IssueCouponCommand;
import com.live_commerce.coupon.application.dto.result.IssuedCouponResult;
import java.util.UUID;

public interface IssueCouponUseCase {
    IssuedCouponResult issueCoupon(IssueCouponCommand command);
    IssuedCouponResult issueFirstJoinCoupon(UUID userId);
}
