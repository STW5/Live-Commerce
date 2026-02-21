package com.live_commerce.ai.application.service;

import java.util.List;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.live_commerce.ai.application.dto.request.AiSearchCondition;
import com.live_commerce.ai.application.dto.result.AiResult;
import com.live_commerce.ai.application.exception.AiExceptionCode;
import com.live_commerce.ai.application.exception.CustomException;
import com.live_commerce.ai.domain.model.AI;
import com.live_commerce.ai.domain.port.in.GetAiAnalysisListUseCase;
import com.live_commerce.ai.domain.port.out.AiRepositoryPort;
import com.live_commerce.ai.infrastructure.security.RequestUserDetails;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GetAiAnalysisListService implements GetAiAnalysisListUseCase {

	private static final Set<Integer> ALLOWED_PAGE_SIZES = Set.of(10, 30, 50);

	private final AiRepositoryPort aiRepositoryPort;

	@Override
	public Page<AiResult> getAiAnalysisList(AiSearchCondition condition, Pageable pageable,
			RequestUserDetails userDetails) {
		validateMasterRole(userDetails);

		if (!ALLOWED_PAGE_SIZES.contains(pageable.getPageSize())) {
			pageable = PageRequest.of(pageable.getPageNumber(), 10, pageable.getSort());
		}

		List<AI> ais = aiRepositoryPort.searchAi(condition);
		List<AiResult> results = ais.stream().map(AiResult::from).toList();
		return new PageImpl<>(results, pageable, results.size());
	}

	private void validateMasterRole(RequestUserDetails userDetails) {
		boolean isMaster = userDetails.getAuthorities().stream()
			.anyMatch(auth -> auth.getAuthority().equals("ROLE_MASTER"));
		if (!isMaster) {
			throw new CustomException(AiExceptionCode.FORBIDDEN);
		}
	}
}
