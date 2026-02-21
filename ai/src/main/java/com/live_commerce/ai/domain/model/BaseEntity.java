package com.live_commerce.ai.domain.model;

import java.time.LocalDateTime;

import lombok.Getter;

@Getter
public abstract class BaseEntity {

	protected LocalDateTime createdAt;
	protected LocalDateTime updatedAt;
	protected boolean deletedStatus = false;
	protected LocalDateTime deletedAt;
	protected String createdBy;
	protected String updatedBy;
	protected String deletedBy;

	public void markAsDeleted(String deletedBy) {
		this.deletedStatus = true;
		this.deletedAt = LocalDateTime.now();
		this.deletedBy = deletedBy;
	}

}
