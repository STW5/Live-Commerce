package com.live_commerce.ai.domain.model;

import java.time.LocalDateTime;
import java.util.UUID;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AI extends BaseEntity {

	private UUID id;
	private UUID liveBroadcastId;
	private String requestPayload;
	private String responsePayload;

	// 신규 생성용 팩토리 메서드 (id 없음 — Adapter에서 생성)
	public static AI of(UUID liveBroadcastId, String requestPayload, String responsePayload) {
		AI ai = new AI();
		ai.liveBroadcastId = liveBroadcastId;
		ai.requestPayload = requestPayload;
		ai.responsePayload = responsePayload;
		return ai;
	}

	// 복원용 팩토리 메서드 (DB에서 로드 시 Adapter가 사용)
	public static AI of(UUID id, UUID liveBroadcastId, String requestPayload, String responsePayload,
			LocalDateTime createdAt, LocalDateTime updatedAt, boolean deletedStatus,
			LocalDateTime deletedAt, String createdBy, String updatedBy, String deletedBy) {
		AI ai = new AI();
		ai.id = id;
		ai.liveBroadcastId = liveBroadcastId;
		ai.requestPayload = requestPayload;
		ai.responsePayload = responsePayload;
		ai.createdAt = createdAt;
		ai.updatedAt = updatedAt;
		ai.deletedStatus = deletedStatus;
		ai.deletedAt = deletedAt;
		ai.createdBy = createdBy;
		ai.updatedBy = updatedBy;
		ai.deletedBy = deletedBy;
		return ai;
	}
}
