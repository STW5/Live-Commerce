package com.live_commerce.ai.domain.port.out;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.live_commerce.ai.application.dto.request.AiSearchCondition;
import com.live_commerce.ai.application.dto.result.AiResult;
import com.live_commerce.ai.domain.model.AI;

public interface AiRepositoryPort {
	AiResult save(AI ai);
	Optional<AI> findById(UUID id);
	List<AI> searchAi(AiSearchCondition condition);
}
