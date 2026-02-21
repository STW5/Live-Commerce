package com.live_commerce.coupon.domain.port.in;

import com.live_commerce.coupon.application.dto.command.CreateCouponPolicyCommand;
import com.live_commerce.coupon.application.dto.result.CouponPolicyResult;
import java.util.List;

public interface ManageCouponPolicyUseCase {
    CouponPolicyResult createCouponPolicy(CreateCouponPolicyCommand command);
    CouponPolicyResult getCouponPolicy(String code);
    List<CouponPolicyResult> getCouponPolicies();
    void updateCouponPolicy(String code, CreateCouponPolicyCommand command);
    void deleteCouponPolicy(String code);
}
