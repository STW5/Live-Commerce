package com.live_commerce.ai.adapter.out.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.live_commerce.ai.application.dto.request.AiSearchCondition;
import com.live_commerce.ai.application.dto.result.AiResult;
import com.live_commerce.ai.domain.model.AI;
import com.live_commerce.ai.domain.port.out.AiRepositoryPort;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class AiPersistenceAdapter implements AiRepositoryPort {

	private final AiJpaRepository aiJpaRepository;

	@Override
	public AiResult save(AI ai) {
		// 소프트 삭제: 기존 엔티티 로드 후 삭제 상태 적용
		if (ai.isDeletedStatus() && ai.getId() != null) {
			AiJpaEntity entity = aiJpaRepository.findById(ai.getId())
				.orElseThrow(() -> new IllegalStateException("AI entity not found: " + ai.getId()));
			entity.applyDeletion(ai.getDeletedBy());
			return AiResult.from(aiJpaRepository.save(entity).toDomain());
		}

		AiJpaEntity entity = AiJpaEntity.from(ai);
		AiJpaEntity saved = aiJpaRepository.save(entity);
		return AiResult.from(saved.toDomain());
	}

	@Override
	public Optional<AI> findById(UUID id) {
		return aiJpaRepository.findById(id)
			.filter(e -> !e.isDeletedStatus())
			.map(AiJpaEntity::toDomain);
	}

	@Override
	public List<AI> searchAi(AiSearchCondition condition) {
		return aiJpaRepository.searchAi(condition).stream()
			.map(AiJpaEntity::toDomain)
			.toList();
	}
}
