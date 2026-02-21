package com.live_commerce.ai.domain.port.in;

import java.util.UUID;

import com.live_commerce.ai.application.dto.result.AiResult;
import com.live_commerce.ai.infrastructure.security.RequestUserDetails;

public interface GetAiAnalysisUseCase {
	AiResult getAiAnalysis(UUID id, RequestUserDetails userDetails);
}
