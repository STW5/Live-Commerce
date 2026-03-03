package com.live_commerce.product.inventory.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface InventoryJpaRepository extends JpaRepository<InventoryJpaEntity, UUID> {

    Optional<InventoryJpaEntity> findByInventoryIdAndDeletedStatusFalse(UUID inventoryId);

    Optional<InventoryJpaEntity> findByProductIdAndDeletedStatusFalse(UUID productId);

    @Query("""
        SELECT CASE WHEN COUNT(i) > 0 THEN true ELSE false END
        FROM InventoryJpaEntity i
        WHERE i.productId = :productId
          AND i.availableQuantity >= :quantity
          AND i.deletedStatus = false
    """)
    boolean existsOrderableInventory(@Param("productId") UUID productId,
                                     @Param("quantity") int quantity);

    @Modifying(clearAutomatically = true)
    @Query("""
        UPDATE InventoryJpaEntity i
        SET i.quantity = i.quantity - :quantity,
            i.availableQuantity = i.availableQuantity - :quantity
        WHERE i.productId = :productId
          AND i.deletedStatus = false
          AND i.availableQuantity >= :quantity
    """)
    int decreaseInventoryAtomically(@Param("productId") UUID productId,
                                    @Param("quantity") int quantity);

    @Modifying(clearAutomatically = true)
    @Query("""
        UPDATE InventoryJpaEntity i
        SET i.quantity = i.quantity + :quantity,
            i.availableQuantity = i.availableQuantity + :quantity
        WHERE i.productId = :productId
          AND i.deletedStatus = false
    """)
    int increaseInventoryAtomically(@Param("productId") UUID productId,
                                    @Param("quantity") int quantity);
}
