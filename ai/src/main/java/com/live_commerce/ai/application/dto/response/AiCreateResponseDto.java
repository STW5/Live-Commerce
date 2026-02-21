package com.live_commerce.ai.application.dto.response;

import java.util.UUID;

import com.live_commerce.ai.application.dto.result.AiResult;

public record AiCreateResponseDto(
	UUID id,
	UUID liveBroadcastId,
	String requestPayload,
	String responsePayload
) {
	public static AiCreateResponseDto from(AiResult result) {
		return new AiCreateResponseDto(
			result.id(),
			result.liveBroadcastId(),
			result.requestPayload(),
			result.responsePayload()
		);
	}
}
