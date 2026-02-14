package com.live_commerce.order.adapter.out.persistence;

import com.live_commerce.order.domain.model.Order;
import com.live_commerce.order.domain.model.vo.Money;
import com.live_commerce.order.domain.model.vo.OrderQuantity;
import org.springframework.stereotype.Component;

/**
 * 도메인 Order ↔ JPA OrderJpaEntity 변환 컴포넌트
 *
 * [아키텍처 노트]
 * - adapter/out/persistence 레이어 전용
 * - 도메인 Order는 JPA를 모름 → Mapper가 경계에서 변환 담당
 * - toJpaEntity(): 도메인 → JPA (저장 시 사용)
 * - toDomain(): JPA → 도메인 (조회 시 사용, Order.reconstitute() 호출)
 */
@Component
public class OrderMapper {

    /**
     * 도메인 Order → JPA OrderJpaEntity 변환
     */
    public OrderJpaEntity toJpaEntity(Order order) {
        return OrderJpaEntity.builder()
                .id(order.getId())
                .userId(order.getUserId())
                .productId(order.getProductId())
                .productQuantity(order.getQuantity().value())
                .productTotalPrice(order.getTotalPrice().toDouble())
                .finalPaidPrice(order.getFinalPrice().toDouble())
                .requirement(order.getRequirement())
                .status(order.getStatus())
                .broadcastId(order.getBroadcastId())
                .couponId(order.getCouponId())
                .build();
    }

    /**
     * JPA OrderJpaEntity → 도메인 Order 변환
     * Order.reconstitute() 를 통해 DB에서 재구성
     */
    public Order toDomain(OrderJpaEntity entity) {
        return Order.reconstitute(
                entity.getId(),
                entity.getUserId(),
                entity.getProductId(),
                entity.getBroadcastId(),
                entity.getCouponId(),
                new OrderQuantity(entity.getProductQuantity()),
                Money.of(entity.getProductTotalPrice()),
                Money.of(entity.getFinalPaidPrice()),
                entity.getRequirement(),
                entity.getStatus(),
                entity.getCreatedAt()
        );
    }
}
