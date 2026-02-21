package com.live_commerce.livebroadcast.domain.model;

import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
public abstract class BaseEntity {

    private LocalDateTime createdAt;
    private String createdBy;
    private LocalDateTime updatedAt;
    private String updatedBy;
    private LocalDateTime deletedAt;
    private UUID deletedBy;
    private boolean deletedStatus;

    protected BaseEntity() {
        this.deletedStatus = false;
    }

    public void delete(UUID deletedById) {
        this.deletedStatus = true;
        this.deletedAt = LocalDateTime.now();
        this.deletedBy = deletedById;
    }

    protected void setAuditFields(LocalDateTime createdAt, String createdBy,
            LocalDateTime updatedAt, String updatedBy,
            LocalDateTime deletedAt, UUID deletedBy, boolean deletedStatus) {
        this.createdAt = createdAt;
        this.createdBy = createdBy;
        this.updatedAt = updatedAt;
        this.updatedBy = updatedBy;
        this.deletedAt = deletedAt;
        this.deletedBy = deletedBy;
        this.deletedStatus = deletedStatus;
    }
}
