package com.live_commerce.payment.infrastructure.adapter.persistence;

import java.util.List;

import org.springframework.data.domain.Pageable;

import com.live_commerce.payment.application.dto.request.PaymentSearchCondition;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.jpa.impl.JPAQueryFactory;

import lombok.RequiredArgsConstructor;

/**
 * 결제 QueryDSL Repository 구현체 - Adapter Layer 전용
 * {@code domain/repository/PaymentQueryRepositoryImpl}를 대체 (QPaymentJpaEntity 사용)
 */
@RequiredArgsConstructor
public class PaymentQueryJpaRepositoryImpl implements PaymentQueryJpaRepository {

	private final JPAQueryFactory queryFactory;

	@Override
	public List<PaymentJpaEntity> searchPayment(PaymentSearchCondition condition, Pageable pageable) {
		QPaymentJpaEntity payment = QPaymentJpaEntity.paymentJpaEntity;
		BooleanBuilder builder = new BooleanBuilder();

		builder.and(payment.deletedStatus.isFalse());

		if (condition.userId() != null) {
			builder.and(payment.userId.eq(condition.userId()));
		}
		if (condition.orderId() != null) {
			builder.and(payment.orderId.eq(condition.orderId()));
		}
		if (condition.status() != null) {
			builder.and(payment.status.eq(condition.status()));
		}
		if (condition.createdAtFrom() != null) {
			builder.and(payment.createdAt.goe(condition.createdAtFrom()));
		}
		if (condition.createdAtTo() != null) {
			builder.and(payment.createdAt.loe(condition.createdAtTo()));
		}

		return queryFactory
			.selectFrom(payment)
			.where(builder)
			.orderBy(payment.createdAt.desc())
			.offset(pageable.getOffset())
			.limit(pageable.getPageSize())
			.fetch();
	}

	@Override
	public long countPayment(PaymentSearchCondition condition) {
		QPaymentJpaEntity payment = QPaymentJpaEntity.paymentJpaEntity;
		BooleanBuilder builder = new BooleanBuilder();

		builder.and(payment.deletedStatus.isFalse());

		if (condition.userId() != null) {
			builder.and(payment.userId.eq(condition.userId()));
		}
		if (condition.orderId() != null) {
			builder.and(payment.orderId.eq(condition.orderId()));
		}
		if (condition.status() != null) {
			builder.and(payment.status.eq(condition.status()));
		}
		if (condition.createdAtFrom() != null) {
			builder.and(payment.createdAt.goe(condition.createdAtFrom()));
		}
		if (condition.createdAtTo() != null) {
			builder.and(payment.createdAt.loe(condition.createdAtTo()));
		}

		return queryFactory
			.select(payment.count())
			.from(payment)
			.where(builder)
			.fetchOne();
	}
}
