package com.live_commerce.product.inventory.domain.model;

import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
public abstract class BaseEntity {

    protected LocalDateTime createdAt;
    protected String createdBy;
    protected LocalDateTime updatedAt;
    protected String updatedBy;
    protected LocalDateTime deletedAt;
    protected UUID deletedBy;
    protected boolean deletedStatus;

    protected BaseEntity() {
        this.deletedStatus = false;
    }

    public void delete(UUID deletedBy) {
        this.deletedStatus = true;
        this.deletedAt = LocalDateTime.now();
        this.deletedBy = deletedBy;
    }
}
