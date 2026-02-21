package com.live_commerce.ai.application.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.live_commerce.ai.application.dto.result.AiResult;
import com.live_commerce.ai.application.exception.AiExceptionCode;
import com.live_commerce.ai.application.exception.CustomException;
import com.live_commerce.ai.domain.model.AI;
import com.live_commerce.ai.domain.port.in.GetAiAnalysisUseCase;
import com.live_commerce.ai.domain.port.out.AiRepositoryPort;
import com.live_commerce.ai.infrastructure.security.RequestUserDetails;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GetAiAnalysisService implements GetAiAnalysisUseCase {

	private final AiRepositoryPort aiRepositoryPort;

	@Override
	public AiResult getAiAnalysis(UUID id, RequestUserDetails userDetails) {
		validateMasterRole(userDetails);
		AI ai = aiRepositoryPort.findById(id)
			.orElseThrow(() -> new CustomException(AiExceptionCode.ANALYSIS_NOT_FOUND));
		return AiResult.from(ai);
	}

	private void validateMasterRole(RequestUserDetails userDetails) {
		boolean isMaster = userDetails.getAuthorities().stream()
			.anyMatch(auth -> auth.getAuthority().equals("ROLE_MASTER"));
		if (!isMaster) {
			throw new CustomException(AiExceptionCode.FORBIDDEN);
		}
	}
}
