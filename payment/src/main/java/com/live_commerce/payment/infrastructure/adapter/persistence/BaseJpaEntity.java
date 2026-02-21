package com.live_commerce.payment.infrastructure.adapter.persistence;

import java.time.LocalDateTime;

import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;

/**
 * JPA 감사 베이스 엔티티 - Adapter Layer 전용
 * Domain Layer의 BaseEntity(@Deprecated)를 대체
 */
@Getter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseJpaEntity {

	@CreatedDate
	@Column(updatable = false, nullable = false)
	private LocalDateTime createdAt;

	@LastModifiedDate
	private LocalDateTime updatedAt;

	@Column(nullable = false)
	private boolean deletedStatus = false;

	private LocalDateTime deletedAt;

	@CreatedBy
	@Column(updatable = false)
	private String createdBy;

	@LastModifiedBy
	private String updatedBy;

	private String deletedBy;

	public void markAsDeleted(String deletedBy) {
		this.deletedStatus = true;
		this.deletedAt = LocalDateTime.now();
		this.deletedBy = deletedBy;
	}
}
