package com.live_commerce.livebroadcast.adapter.out.persistence.entity;

import com.live_commerce.livebroadcast.domain.model.BroadcastSubscription;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

import java.util.UUID;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "p_broadcast_subscriptions", schema = "livebroadcast",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "broadcast_id"}))
public class BroadcastSubscriptionJpaEntity extends BaseJpaEntity {

    @Id
    @UuidGenerator
    private UUID subscriptionId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "broadcast_id", nullable = false)
    private UUID broadcastId;

    public static BroadcastSubscriptionJpaEntity fromDomain(BroadcastSubscription domain) {
        BroadcastSubscriptionJpaEntity e = new BroadcastSubscriptionJpaEntity();
        e.subscriptionId = domain.getSubscriptionId();
        e.userId = domain.getUserId();
        e.broadcastId = domain.getBroadcastId();
        return e;
    }

    public BroadcastSubscription toDomain() {
        return BroadcastSubscription.reconstitute(
                subscriptionId, userId, broadcastId,
                getCreatedAt(), getCreatedBy(), getUpdatedAt(), getUpdatedBy(),
                getDeletedAt(), getDeletedBy(), isDeletedStatus());
    }
}
