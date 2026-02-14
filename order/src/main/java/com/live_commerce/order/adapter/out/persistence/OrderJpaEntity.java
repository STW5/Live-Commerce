package com.live_commerce.order.adapter.out.persistence;

import com.live_commerce.order.domain.model.OrderStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * JPA 전용 Order 엔티티 - adapter/out/persistence 레이어 전용
 *
 * [아키텍처 노트]
 * - 도메인 Order와 완전히 분리된 JPA 매핑 클래스
 * - OrderMapper를 통해 도메인 Order ↔ OrderJpaEntity 변환
 * - BaseJpaEntity 상속: 감사 필드(createdAt, createdBy, updatedAt 등)
 */
@Entity
@Table(name = "p_order", schema = "orders")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderJpaEntity extends BaseJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "product_id")
    private UUID productId;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "product_quantity")
    private Integer productQuantity;

    @Column(name = "product_total_price")
    private Double productTotalPrice;

    @Column(name = "requirement")
    private String requirement;

    @Column(name = "status")
    @Enumerated(EnumType.STRING)
    private OrderStatus status;

    @Column(name = "broadcast_id")
    private UUID broadcastId;

    @Column(name = "coupon_id")
    private UUID couponId;

    @Column(name = "final_paid_price")
    private Double finalPaidPrice;
}
