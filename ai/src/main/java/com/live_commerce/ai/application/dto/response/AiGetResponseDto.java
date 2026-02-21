package com.live_commerce.ai.application.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

import com.live_commerce.ai.application.dto.result.AiResult;

public record AiGetResponseDto(
	UUID id,
	UUID liveBroadcastId,
	String requestPayload,
	String responsePayload,
	LocalDateTime createdAt,
	LocalDateTime updatedAt,
	String createdBy,
	String updatedBy
) {
	public static AiGetResponseDto from(AiResult result) {
		return new AiGetResponseDto(
			result.id(),
			result.liveBroadcastId(),
			result.requestPayload(),
			result.responsePayload(),
			result.createdAt(),
			result.updatedAt(),
			result.createdBy(),
			result.updatedBy()
		);
	}
}
