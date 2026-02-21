package com.live_commerce.ai.domain.port.in;

import java.util.UUID;

import com.live_commerce.ai.infrastructure.security.RequestUserDetails;

public interface DeleteAiAnalysisUseCase {
	void deleteAiAnalysis(UUID id, RequestUserDetails userDetails);
}
