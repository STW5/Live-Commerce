package com.live_commerce.coupon.infrastructure.client;

import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "order", url = "${gateway.base-url}", path = "/api/v1")
public interface OrderClient {

    @GetMapping("/orders/{orderId}")
    OrderResponse getOrder(@PathVariable("orderId") UUID orderId);

    record OrderResponse(
            UUID id,
            UUID userId,
            UUID couponId,
            String status
    ) {}
}
