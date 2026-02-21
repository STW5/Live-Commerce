package com.live_commerce.ai.adapter.out.persistence;

import java.util.UUID;

import org.hibernate.annotations.UuidGenerator;

import com.live_commerce.ai.domain.model.AI;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "p_ai")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiJpaEntity extends BaseJpaEntity {

	@Id
	@UuidGenerator
	private UUID id;

	@Column(nullable = false)
	private UUID liveBroadcastId;

	@Column(nullable = false, columnDefinition = "TEXT")
	private String requestPayload;

	@Column(nullable = false, columnDefinition = "TEXT")
	private String responsePayload;

	public AI toDomain() {
		return AI.of(
			this.id,
			this.liveBroadcastId,
			this.requestPayload,
			this.responsePayload,
			this.getCreatedAt(),
			this.getUpdatedAt(),
			this.isDeletedStatus(),
			this.getDeletedAt(),
			this.getCreatedBy(),
			this.getUpdatedBy(),
			this.getDeletedBy()
		);
	}

	public static AiJpaEntity from(AI ai) {
		AiJpaEntity entity = new AiJpaEntity();
		entity.id = ai.getId();
		entity.liveBroadcastId = ai.getLiveBroadcastId();
		entity.requestPayload = ai.getRequestPayload();
		entity.responsePayload = ai.getResponsePayload();
		return entity;
	}
}
