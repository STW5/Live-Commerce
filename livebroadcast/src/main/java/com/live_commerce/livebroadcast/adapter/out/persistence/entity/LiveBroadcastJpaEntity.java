package com.live_commerce.livebroadcast.adapter.out.persistence.entity;

import com.live_commerce.livebroadcast.domain.model.BroadcastStatus;
import com.live_commerce.livebroadcast.domain.model.LiveBroadcast;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "p_live_broadcast", schema = "livebroadcast")
public class LiveBroadcastJpaEntity extends BaseJpaEntity {

    @Id
    @UuidGenerator
    private UUID liveBroadcastId;

    @Column(nullable = false)
    private String broadcastName;

    private LocalDateTime startTime;
    private LocalDateTime endTime;

    @Enumerated(EnumType.STRING)
    private BroadcastStatus broadcastStatus;

    private UUID hostId;
    private UUID companyId;
    private Integer totalViewerCount;

    public static LiveBroadcastJpaEntity fromDomain(LiveBroadcast domain) {
        LiveBroadcastJpaEntity e = new LiveBroadcastJpaEntity();
        e.liveBroadcastId = domain.getLiveBroadcastId();
        e.broadcastName = domain.getBroadcastName();
        e.startTime = domain.getStartTime();
        e.endTime = domain.getEndTime();
        e.broadcastStatus = domain.getBroadcastStatus();
        e.hostId = domain.getHostId();
        e.companyId = domain.getCompanyId();
        e.totalViewerCount = domain.getTotalViewerCount();
        return e;
    }

    public LiveBroadcast toDomain() {
        return LiveBroadcast.reconstitute(
                liveBroadcastId, broadcastName, startTime, endTime,
                broadcastStatus, hostId, companyId, totalViewerCount,
                getCreatedAt(), getCreatedBy(), getUpdatedAt(), getUpdatedBy(),
                getDeletedAt(), getDeletedBy(), isDeletedStatus());
    }
}
