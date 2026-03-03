package com.live_commerce.product.product.adapter.out.persistence;

import com.live_commerce.product.product.domain.model.ProductDiscount;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "p_product_discount", schema = "products")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class ProductDiscountJpaEntity extends BaseJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    private Integer discountPrice;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private UUID appliedBy;

    public static ProductDiscountJpaEntity from(ProductDiscount domain) {
        return ProductDiscountJpaEntity.builder()
                .id(domain.getId())
                .productId(domain.getProductId())
                .discountPrice(domain.getDiscountPrice())
                .startAt(domain.getStartAt())
                .endAt(domain.getEndAt())
                .appliedBy(domain.getAppliedBy())
                .build();
    }

    public ProductDiscount toDomain() {
        return ProductDiscount.builder()
                .id(this.id)
                .productId(this.productId)
                .discountPrice(this.discountPrice)
                .startAt(this.startAt)
                .endAt(this.endAt)
                .appliedBy(this.appliedBy)
                .build();
    }
}
