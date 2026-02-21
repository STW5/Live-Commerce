package com.live_commerce.coupon.adapter.out.client;

import com.live_commerce.coupon.domain.port.out.OrderQueryPort;
import com.live_commerce.coupon.infrastructure.client.OrderClient;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Order 조회 Feign 어댑터 (OrderQueryPort 구현체)
 * 보상 트랜잭션 시 주문 정보 조회에 사용
 */
@Component
@RequiredArgsConstructor
public class OrderFeignAdapter implements OrderQueryPort {

    private final OrderClient orderClient;

    @Override
    public OrderInfo getOrder(UUID orderId) {
        OrderClient.OrderResponse response = orderClient.getOrder(orderId);
        return new OrderInfo(response.id(), response.userId(), response.couponId());
    }
}
