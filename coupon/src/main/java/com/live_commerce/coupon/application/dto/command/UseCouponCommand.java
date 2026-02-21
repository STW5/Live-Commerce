package com.live_commerce.coupon.application.dto.command;

import java.util.UUID;

public record UseCouponCommand(UUID couponId, UUID userId) {}
