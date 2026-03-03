package com.live_commerce.product.product.domain.model;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product extends BaseEntity {

    private UUID productId;
    private UUID companyId;
    private String name;
    private String description;
    private Integer price;
    private ProductCategory category;
    private ProductStatus productStatus;

    @Builder
    private Product(UUID productId, UUID companyId, String name, String description,
                    Integer price, ProductCategory category, ProductStatus productStatus) {
        this.productId = productId;
        this.companyId = companyId;
        this.name = name;
        this.description = description;
        this.price = price;
        this.category = category;
        this.productStatus = productStatus;
    }

    public static Product create(UUID companyId, String name, String description,
                                 Integer price, ProductCategory category) {
        return Product.builder()
                .companyId(companyId)
                .name(name)
                .description(description)
                .price(price)
                .category(category)
                .productStatus(ProductStatus.PREPARING)
                .build();
    }

    public void update(String name, String description, Integer price,
                       ProductCategory category, ProductStatus productStatus) {
        if (name != null) this.name = name;
        if (description != null) this.description = description;
        if (price != null) this.price = price;
        if (category != null) this.category = category;
        if (productStatus != null) this.productStatus = productStatus;
    }

    public void changeStatus(ProductStatus productStatus) {
        this.productStatus = productStatus;
    }
}
