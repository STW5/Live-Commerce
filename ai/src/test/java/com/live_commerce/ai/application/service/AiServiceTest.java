package com.live_commerce.ai.application.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;

import com.live_commerce.ai.adapter.out.persistence.AiJpaRepository;
import com.live_commerce.ai.application.dto.command.AnalyzeCommand;
import com.live_commerce.ai.application.dto.result.AiResult;
import com.live_commerce.ai.application.exception.AiExceptionCode;
import com.live_commerce.ai.application.exception.CustomException;
import com.live_commerce.ai.domain.port.in.AnalyzeUseCase;
import com.live_commerce.ai.domain.port.in.DeleteAiAnalysisUseCase;
import com.live_commerce.ai.domain.port.in.GetAiAnalysisUseCase;
import com.live_commerce.ai.domain.port.out.AiNotificationPort;
import com.live_commerce.ai.domain.port.out.AiRepositoryPort;
import com.live_commerce.ai.domain.port.out.GeminiPort;
import com.live_commerce.ai.domain.prompt.PromptGenerator;
import com.live_commerce.ai.infrastructure.security.RequestUserDetails;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AiServiceTest {

	@Autowired
	private AnalyzeUseCase analyzeUseCase;

	@Autowired
	private GetAiAnalysisUseCase getAiAnalysisUseCase;

	@Autowired
	private DeleteAiAnalysisUseCase deleteAiAnalysisUseCase;

	@Autowired
	private AiJpaRepository aiJpaRepository;

	@MockitoBean
	private PromptGenerator promptGenerator;

	@MockitoBean
	private GeminiPort geminiPort;

	@MockitoBean
	private AiNotificationPort aiNotificationPort;

	private final UUID broadcastId = UUID.randomUUID();
	private final RequestUserDetails master =
		new RequestUserDetails(UUID.randomUUID(), "master", List.of(() -> "ROLE_MASTER"));
	private final RequestUserDetails customer =
		new RequestUserDetails(UUID.randomUUID(), "user", List.of(() -> "ROLE_CUSTOMER"));

	@Test
	@DisplayName("내부 시크릿 불일치 → UNAUTHORIZED_INTERNAL_REQUEST 예외")
	void analyze_invalidSecret() {
		AnalyzeCommand command = new AnalyzeCommand(broadcastId, List.of(), "wrong-secret");

		CustomException ex = catchThrowableOfType(
			() -> analyzeUseCase.analyze(command),
			CustomException.class
		);

		assertThat(ex.getExceptionCode()).isEqualTo(AiExceptionCode.UNAUTHORIZED_INTERNAL_REQUEST);
	}

	@Test
	@DisplayName("50개 초과 메시지는 최근 50개만 사용")
	void analyze_success_withTrimming() {
		List<String> messages = IntStream.range(0, 60)
			.mapToObj(i -> "msg" + i)
			.toList();

		AnalyzeCommand command = new AnalyzeCommand(broadcastId, messages, "valid-secret");

		when(promptGenerator.generate(any())).thenReturn("prompt");
		when(geminiPort.generateText("prompt")).thenReturn("summary");

		AiResult result = analyzeUseCase.analyze(command);

		assertThat(result.responsePayload()).isEqualTo("summary");
		verify(aiNotificationPort).sendAnalysisResult(eq("UXXXXXX"), contains("채팅 분석 완료"));
		assertThat(aiJpaRepository.count()).isEqualTo(1);
	}

	@Test
	@DisplayName("권한 없는 유저 조회 → FORBIDDEN 예외")
	void getAiAnalysis_forbidden() {
		UUID id = UUID.randomUUID();

		CustomException ex = catchThrowableOfType(
			() -> getAiAnalysisUseCase.getAiAnalysis(id, customer),
			CustomException.class
		);

		assertThat(ex.getExceptionCode()).isEqualTo(AiExceptionCode.FORBIDDEN);
	}

	@Test
	@DisplayName("마스터 조회 성공")
	void getAiAnalysis_master_success() {
		when(promptGenerator.generate(any())).thenReturn("prompt");
		when(geminiPort.generateText(any())).thenReturn("응답");

		AnalyzeCommand command = new AnalyzeCommand(broadcastId, List.of("msg1"), "valid-secret");
		AiResult saved = analyzeUseCase.analyze(command);

		AiResult found = getAiAnalysisUseCase.getAiAnalysis(saved.id(), master);

		assertThat(found.responsePayload()).isEqualTo("응답");
	}

	@Test
	@DisplayName("삭제 성공 - 마스터")
	void deleteAiAnalysis_success() {
		when(promptGenerator.generate(any())).thenReturn("prompt");
		when(geminiPort.generateText(any())).thenReturn("응답");

		AnalyzeCommand command = new AnalyzeCommand(broadcastId, List.of("msg1"), "valid-secret");
		AiResult saved = analyzeUseCase.analyze(command);

		deleteAiAnalysisUseCase.deleteAiAnalysis(saved.id(), master);

		assertThat(aiJpaRepository.findById(saved.id()))
			.isPresent()
			.get()
			.matches(e -> e.isDeletedStatus());
	}

	@Test
	@DisplayName("삭제 실패 - 권한 없음")
	void deleteAiAnalysis_forbidden() {
		UUID id = UUID.randomUUID();

		CustomException ex = catchThrowableOfType(
			() -> deleteAiAnalysisUseCase.deleteAiAnalysis(id, customer),
			CustomException.class
		);

		assertThat(ex.getExceptionCode()).isEqualTo(AiExceptionCode.FORBIDDEN);
	}
}
