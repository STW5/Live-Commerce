package com.live_commerce.ai.application.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.live_commerce.ai.application.dto.command.AnalyzeCommand;
import com.live_commerce.ai.application.dto.result.AiResult;
import com.live_commerce.ai.application.exception.AiExceptionCode;
import com.live_commerce.ai.application.exception.CustomException;
import com.live_commerce.ai.domain.model.AI;
import com.live_commerce.ai.domain.port.in.AnalyzeUseCase;
import com.live_commerce.ai.domain.port.out.AiNotificationPort;
import com.live_commerce.ai.domain.port.out.AiRepositoryPort;
import com.live_commerce.ai.domain.port.out.GeminiPort;
import com.live_commerce.ai.domain.prompt.PromptGenerator;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AnalyzeService implements AnalyzeUseCase {

	private final AiRepositoryPort aiRepositoryPort;
	private final GeminiPort geminiPort;
	private final AiNotificationPort aiNotificationPort;
	private final PromptGenerator promptGenerator;
	private final ObjectMapper objectMapper;

	@Value("${internal.secret}")
	private String internalSecret;

	@Value("${slack.admin-user-id}")
	private String adminSlackUserId;

	private static final int MAX_CHAT_MESSAGES = 50;

	@Override
	public AiResult analyze(AnalyzeCommand command) {
		if (!internalSecret.equals(command.secret())) {
			throw new CustomException(AiExceptionCode.UNAUTHORIZED_INTERNAL_REQUEST);
		}

		List<String> messages = command.messages();
		List<String> trimmed = messages.size() > MAX_CHAT_MESSAGES
			? messages.subList(messages.size() - MAX_CHAT_MESSAGES, messages.size())
			: messages;

		String prompt = promptGenerator.generate(trimmed);
		String response = geminiPort.generateText(prompt);
		String requestPayloadJson = serializeMessages(command);

		aiNotificationPort.sendAnalysisResult(adminSlackUserId, "채팅 분석 완료\n\n" + response);

		AI ai = AI.of(command.liveBroadcastId(), requestPayloadJson, response);
		return aiRepositoryPort.save(ai);
	}

	private String serializeMessages(AnalyzeCommand command) {
		try {
			return objectMapper.writeValueAsString(command);
		} catch (Exception e) {
			throw new CustomException(AiExceptionCode.SERIALIZATION_ERROR);
		}
	}
}
