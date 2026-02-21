package com.live_commerce.coupon.application.dto.command;

import java.util.UUID;

public record IssueCouponCommand(UUID userId, String couponCode) {}
