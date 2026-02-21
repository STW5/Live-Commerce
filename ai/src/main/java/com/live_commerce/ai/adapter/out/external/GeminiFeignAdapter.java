package com.live_commerce.ai.adapter.out.external;

import org.springframework.stereotype.Component;

import com.live_commerce.ai.application.exception.AiExceptionCode;
import com.live_commerce.ai.application.exception.CustomException;
import com.live_commerce.ai.domain.port.out.GeminiPort;
import com.live_commerce.ai.infrastructure.client.GeminiServiceAdapter;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class GeminiFeignAdapter implements GeminiPort {

	private final GeminiServiceAdapter geminiServiceAdapter;

	@Override
	public String generateText(String prompt) {
		try {
			return geminiServiceAdapter.generateText(prompt);
		} catch (Exception e) {
			throw new CustomException(AiExceptionCode.GEMINI_API_ERROR);
		}
	}
}
