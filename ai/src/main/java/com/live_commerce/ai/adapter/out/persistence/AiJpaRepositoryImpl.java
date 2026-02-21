package com.live_commerce.ai.adapter.out.persistence;

import java.util.List;

import com.live_commerce.ai.application.dto.request.AiSearchCondition;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.jpa.impl.JPAQueryFactory;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class AiJpaRepositoryImpl implements AiQueryJpaRepository {

	private final JPAQueryFactory queryFactory;

	@Override
	public List<AiJpaEntity> searchAi(AiSearchCondition condition) {
		QAiJpaEntity ai = QAiJpaEntity.aiJpaEntity;
		BooleanBuilder builder = new BooleanBuilder();

		if (condition.liveBroadcastId() != null) {
			builder.and(ai.liveBroadcastId.eq(condition.liveBroadcastId()));
		}
		if (condition.createdFrom() != null) {
			builder.and(ai.createdAt.goe(condition.createdFrom()));
		}
		if (condition.createdTo() != null) {
			builder.and(ai.createdAt.loe(condition.createdTo()));
		}
		builder.and(ai.deletedStatus.isFalse());

		return queryFactory
			.selectFrom(ai)
			.where(builder)
			.orderBy(ai.createdAt.desc())
			.fetch();
	}
}
