package com.live_commerce.coupon.domain.port.in;

import com.live_commerce.coupon.application.dto.command.UseCouponCommand;
import com.live_commerce.coupon.application.dto.result.IssuedCouponResult;

public interface UseCouponUseCase {
    IssuedCouponResult useCoupon(UseCouponCommand command);
}
