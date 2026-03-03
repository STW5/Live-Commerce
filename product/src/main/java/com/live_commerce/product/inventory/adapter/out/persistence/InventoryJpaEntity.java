package com.live_commerce.product.inventory.adapter.out.persistence;

import com.live_commerce.product.inventory.domain.model.Inventory;
import com.live_commerce.product.inventory.domain.model.InventoryStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.util.UUID;

@Entity
@Table(name = "p_inventory", schema = "products")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class InventoryJpaEntity extends BaseJpaEntity {

    @Id
    @UuidGenerator
    private UUID inventoryId;

    @Column(nullable = false)
    private UUID productId;

    private Integer quantity;
    private Integer reservedQuantity;
    private Integer availableQuantity;

    @Enumerated(EnumType.STRING)
    private InventoryStatus inventoryStatus;

    public static InventoryJpaEntity from(Inventory domain) {
        return InventoryJpaEntity.builder()
                .inventoryId(domain.getInventoryId())
                .productId(domain.getProductId())
                .quantity(domain.getQuantity())
                .reservedQuantity(domain.getReservedQuantity())
                .availableQuantity(domain.getAvailableQuantity())
                .inventoryStatus(domain.getInventoryStatus())
                .build();
    }

    public Inventory toDomain() {
        return Inventory.builder()
                .inventoryId(this.inventoryId)
                .productId(this.productId)
                .quantity(this.quantity)
                .reservedQuantity(this.reservedQuantity)
                .availableQuantity(this.availableQuantity)
                .inventoryStatus(this.inventoryStatus)
                .build();
    }
}
