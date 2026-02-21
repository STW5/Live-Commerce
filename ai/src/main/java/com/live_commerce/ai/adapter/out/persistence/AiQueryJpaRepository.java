package com.live_commerce.ai.adapter.out.persistence;

import java.util.List;

import com.live_commerce.ai.application.dto.request.AiSearchCondition;

public interface AiQueryJpaRepository {
	List<AiJpaEntity> searchAi(AiSearchCondition condition);
}
