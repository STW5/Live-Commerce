package com.live_commerce.livebroadcast.adapter.out.persistence.entity;

import com.live_commerce.livebroadcast.domain.model.BroadcastProduct;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

import java.util.UUID;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "p_broadcast_product", schema = "livebroadcast")
public class BroadcastProductJpaEntity extends BaseJpaEntity {

    @Id
    @UuidGenerator
    private UUID broadcastProductId;

    private UUID liveBroadcastId;
    private UUID productId;

    public static BroadcastProductJpaEntity fromDomain(BroadcastProduct domain) {
        BroadcastProductJpaEntity e = new BroadcastProductJpaEntity();
        e.broadcastProductId = domain.getBroadcastProductId();
        e.liveBroadcastId = domain.getLiveBroadcastId();
        e.productId = domain.getProductId();
        return e;
    }

    public BroadcastProduct toDomain() {
        return BroadcastProduct.reconstitute(
                broadcastProductId, liveBroadcastId, productId,
                getCreatedAt(), getCreatedBy(), getUpdatedAt(), getUpdatedBy(),
                getDeletedAt(), getDeletedBy(), isDeletedStatus());
    }
}
