package com.live_commerce.coupon.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import java.time.LocalDateTime;
import lombok.Getter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * JPA 기본 엔티티 (Adapter Layer)
 * - 도메인 레이어의 BaseEntity를 대체
 * - JPA Auditing 전담
 */
@Getter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseJpaEntity {

    @CreatedBy
    @Column(updatable = false)
    private String createdBy;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedBy
    private String updatedBy;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    private String deletedBy;
    private LocalDateTime deletedAt;
    private Boolean deletedStatus = false;

    public void delete(String deletedBy) {
        if (Boolean.TRUE.equals(this.deletedStatus)) {
            throw new IllegalStateException("Already deleted");
        }
        this.deletedStatus = true;
        this.deletedBy = deletedBy;
        this.deletedAt = LocalDateTime.now();
    }
}
