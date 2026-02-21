package com.live_commerce.ai.domain.port.out;

public interface AiNotificationPort {
	void sendAnalysisResult(String userId, String message);
}
