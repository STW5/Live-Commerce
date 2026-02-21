package com.live_commerce.user.infrastructure.adapter.persistence;

import java.util.List;

import com.live_commerce.user.application.dto.auth.request.UserSearchCondition;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.jpa.impl.JPAQueryFactory;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class UserQueryJpaRepositoryImpl implements UserQueryJpaRepository {

	private final JPAQueryFactory queryFactory;

	@Override
	public List<UserJpaEntity> searchUser(UserSearchCondition condition) {
		QUserJpaEntity user = QUserJpaEntity.userJpaEntity;
		BooleanBuilder builder = new BooleanBuilder();

		if (condition.username() != null) {
			builder.and(user.username.eq(condition.username()));
		}
		if (condition.email() != null) {
			builder.and(user.email.eq(condition.email()));
		}
		if (condition.nickname() != null) {
			builder.and(user.nickname.containsIgnoreCase(condition.nickname()));
		}
		if (condition.userRole() != null) {
			builder.and(user.userRole.eq(condition.userRole()));
		}
		if (condition.alarmConsent() != null) {
			builder.and(user.alarmConsent.eq(condition.alarmConsent()));
		}

		return queryFactory
			.selectFrom(user)
			.where(builder)
			.fetch();
	}
}
