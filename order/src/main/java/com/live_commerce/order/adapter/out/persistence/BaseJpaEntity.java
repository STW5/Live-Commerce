package com.live_commerce.order.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * JPA 전용 베이스 엔티티 - adapter/out/persistence 레이어 전용
 * 기존 presentation.common.BaseEntity를 올바른 위치로 이전
 * (Order domain 엔티티는 이 클래스를 상속하지 않음)
 */
@Getter
@EntityListeners(AuditingEntityListener.class)
@MappedSuperclass
public abstract class BaseJpaEntity {

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @CreatedBy
    @Column(updatable = false)
    private String createdBy;

    private LocalDateTime updatedAt;
    private String updatedBy;
    private LocalDateTime deletedAt;
    private String deletedBy;
    protected Boolean deletedStatus;

    public void delete(String deletedBy) {
        this.deletedStatus = Boolean.TRUE;
        this.deletedAt = LocalDateTime.now();
        this.deletedBy = deletedBy;
    }
}
