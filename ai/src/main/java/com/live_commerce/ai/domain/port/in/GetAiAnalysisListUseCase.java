package com.live_commerce.ai.domain.port.in;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.live_commerce.ai.application.dto.request.AiSearchCondition;
import com.live_commerce.ai.application.dto.result.AiResult;
import com.live_commerce.ai.infrastructure.security.RequestUserDetails;

public interface GetAiAnalysisListUseCase {
	Page<AiResult> getAiAnalysisList(AiSearchCondition condition, Pageable pageable, RequestUserDetails userDetails);
}
