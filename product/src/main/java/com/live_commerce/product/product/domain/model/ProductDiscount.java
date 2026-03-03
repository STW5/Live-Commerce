package com.live_commerce.product.product.domain.model;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductDiscount extends BaseEntity {

    private Long id;
    private UUID productId;
    private Integer discountPrice;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private UUID appliedBy;

    @Builder
    public ProductDiscount(Long id, UUID productId, Integer discountPrice,
                           LocalDateTime startAt, LocalDateTime endAt, UUID appliedBy) {
        this.id = id;
        this.productId = productId;
        this.discountPrice = discountPrice;
        this.startAt = startAt;
        this.endAt = endAt;
        this.appliedBy = appliedBy;
    }

    public static ProductDiscount create(UUID productId, Integer discountPrice,
                                          LocalDateTime startAt, LocalDateTime endAt, UUID appliedBy) {
        return ProductDiscount.builder()
                .productId(productId)
                .discountPrice(discountPrice)
                .startAt(startAt)
                .endAt(endAt)
                .appliedBy(appliedBy)
                .build();
    }

    public boolean isActiveNow() {
        LocalDateTime now = LocalDateTime.now();
        return (startAt.isBefore(now) && endAt.isAfter(now));
    }
}
