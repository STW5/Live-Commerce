package com.live_commerce.ai.presentation.controller;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import com.live_commerce.ai.application.dto.command.AnalyzeCommand;
import com.live_commerce.ai.application.dto.request.AiAnalyzeRequestDto;
import com.live_commerce.ai.application.dto.request.AiSearchCondition;
import com.live_commerce.ai.application.dto.response.AiCreateResponseDto;
import com.live_commerce.ai.application.dto.response.AiGetResponseDto;
import com.live_commerce.ai.application.dto.result.AiResult;
import com.live_commerce.ai.domain.port.in.AnalyzeUseCase;
import com.live_commerce.ai.domain.port.in.DeleteAiAnalysisUseCase;
import com.live_commerce.ai.domain.port.in.GetAiAnalysisListUseCase;
import com.live_commerce.ai.domain.port.in.GetAiAnalysisUseCase;
import com.live_commerce.ai.infrastructure.common.ResponseUtil;
import com.live_commerce.ai.infrastructure.security.RequestUserDetails;
import com.live_commerce.ai.presentation.common.ApiResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
public class AiController {

	private final AnalyzeUseCase analyzeUseCase;
	private final GetAiAnalysisUseCase getAiAnalysisUseCase;
	private final GetAiAnalysisListUseCase getAiAnalysisListUseCase;
	private final DeleteAiAnalysisUseCase deleteAiAnalysisUseCase;

	@PostMapping
	public ResponseEntity<ApiResponse<AiCreateResponseDto>> analyze(
		@RequestHeader(value = "X-Internal-Secret", required = false) String secret,
		@RequestBody AiAnalyzeRequestDto request
	) {
		AnalyzeCommand command = new AnalyzeCommand(
			request.live_broadcast_id(),
			request.request_payload().chat_messages().stream()
				.map(AiAnalyzeRequestDto.ChatMessage::message)
				.toList(),
			secret
		);
		AiResult result = analyzeUseCase.analyze(command);
		return ResponseUtil.success(AiCreateResponseDto.from(result));
	}

	@GetMapping("/{id}")
	@PreAuthorize("hasRole('MASTER')")
	public ResponseEntity<ApiResponse<AiGetResponseDto>> getAiAnalysis(
		@PathVariable UUID id,
		@AuthenticationPrincipal RequestUserDetails requestUserDetails
	) {
		AiResult result = getAiAnalysisUseCase.getAiAnalysis(id, requestUserDetails);
		return ResponseUtil.success(AiGetResponseDto.from(result));
	}

	@GetMapping("/search")
	@PreAuthorize("hasRole('MASTER')")
	public ResponseEntity<ApiResponse<Page<AiGetResponseDto>>> getAiAnalysisList(
		@ModelAttribute AiSearchCondition condition,
		Pageable pageable,
		@AuthenticationPrincipal RequestUserDetails requestUserDetails
	) {
		Page<AiResult> results = getAiAnalysisListUseCase.getAiAnalysisList(condition, pageable,
			requestUserDetails);
		return ResponseUtil.success(results.map(AiGetResponseDto::from));
	}

	@DeleteMapping("/{id}")
	@PreAuthorize("hasRole('MASTER')")
	public ResponseEntity<ApiResponse<Void>> deleteAiAnalysis(
		@PathVariable UUID id,
		@AuthenticationPrincipal RequestUserDetails requestUserDetails
	) {
		deleteAiAnalysisUseCase.deleteAiAnalysis(id, requestUserDetails);
		return ResponseUtil.noContent();
	}
}
