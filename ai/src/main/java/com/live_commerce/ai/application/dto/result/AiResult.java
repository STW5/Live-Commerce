package com.live_commerce.ai.application.dto.result;

import java.time.LocalDateTime;
import java.util.UUID;

import com.live_commerce.ai.domain.model.AI;

public record AiResult(
	UUID id,
	UUID liveBroadcastId,
	String requestPayload,
	String responsePayload,
	LocalDateTime createdAt,
	LocalDateTime updatedAt,
	String createdBy,
	String updatedBy
) {
	public static AiResult from(AI ai) {
		return new AiResult(
			ai.getId(),
			ai.getLiveBroadcastId(),
			ai.getRequestPayload(),
			ai.getResponsePayload(),
			ai.getCreatedAt(),
			ai.getUpdatedAt(),
			ai.getCreatedBy(),
			ai.getUpdatedBy()
		);
	}
}
