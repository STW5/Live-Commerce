package com.live_commerce.product.product.adapter.out.persistence;

import com.live_commerce.product.product.domain.model.Product;
import com.live_commerce.product.product.domain.model.ProductCategory;
import com.live_commerce.product.product.domain.model.ProductStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.util.UUID;

@Entity
@Table(name = "p_product", schema = "products")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class ProductJpaEntity extends BaseJpaEntity {

    @Id
    @UuidGenerator
    private UUID productId;

    private UUID companyId;
    private String name;
    private String description;
    private Integer price;

    @Enumerated(EnumType.STRING)
    private ProductCategory category;

    @Enumerated(EnumType.STRING)
    private ProductStatus productStatus;

    public static ProductJpaEntity from(Product domain) {
        return ProductJpaEntity.builder()
                .productId(domain.getProductId())
                .companyId(domain.getCompanyId())
                .name(domain.getName())
                .description(domain.getDescription())
                .price(domain.getPrice())
                .category(domain.getCategory())
                .productStatus(domain.getProductStatus())
                .build();
    }

    public Product toDomain() {
        Product product = Product.builder()
                .productId(this.productId)
                .companyId(this.companyId)
                .name(this.name)
                .description(this.description)
                .price(this.price)
                .category(this.category)
                .productStatus(this.productStatus)
                .build();
        return product;
    }
}
