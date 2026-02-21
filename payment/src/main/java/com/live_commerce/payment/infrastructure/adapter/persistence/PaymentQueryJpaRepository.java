package com.live_commerce.payment.infrastructure.adapter.persistence;

import java.util.List;

import org.springframework.data.domain.Pageable;

import com.live_commerce.payment.application.dto.request.PaymentSearchCondition;

/**
 * 결제 QueryDSL Repository 인터페이스 - Adapter Layer 전용
 * {@code domain/repository/PaymentQueryRepository}를 대체
 */
public interface PaymentQueryJpaRepository {
	List<PaymentJpaEntity> searchPayment(PaymentSearchCondition condition, Pageable pageable);

	long countPayment(PaymentSearchCondition condition);
}
