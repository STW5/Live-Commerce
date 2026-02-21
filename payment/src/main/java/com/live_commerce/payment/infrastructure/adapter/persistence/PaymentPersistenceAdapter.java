package com.live_commerce.payment.infrastructure.adapter.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import com.live_commerce.payment.application.dto.request.PaymentSearchCondition;
import com.live_commerce.payment.application.port.out.LoadPaymentPort;
import com.live_commerce.payment.application.port.out.SavePaymentPort;
import com.live_commerce.payment.domain.model.Payment;

import lombok.RequiredArgsConstructor;

/**
 * 결제 영속성 어댑터
 * - LoadPaymentPort, SavePaymentPort 구현
 * - PaymentJpaEntity ↔ Payment 도메인 모델 변환 처리
 */
@Component
@RequiredArgsConstructor
public class PaymentPersistenceAdapter implements LoadPaymentPort, SavePaymentPort {

	private final PaymentJpaRepository paymentJpaRepository;

	@Override
	public Optional<Payment> loadById(UUID paymentId) {
		return paymentJpaRepository.findById(paymentId)
			.map(PaymentJpaEntity::toDomain);
	}

	@Override
	public Optional<Payment> loadByOrderId(UUID orderId) {
		return paymentJpaRepository.findByOrderId(orderId)
			.map(PaymentJpaEntity::toDomain);
	}

	@Override
	public List<Payment> search(PaymentSearchCondition condition, Pageable pageable) {
		return paymentJpaRepository.searchPayment(condition, pageable).stream()
			.map(PaymentJpaEntity::toDomain)
			.toList();
	}

	@Override
	public long count(PaymentSearchCondition condition) {
		return paymentJpaRepository.countPayment(condition);
	}

	@Override
	public Payment save(Payment payment) {
		PaymentJpaEntity entity = PaymentJpaEntity.from(payment);
		PaymentJpaEntity saved = paymentJpaRepository.save(entity);
		return saved.toDomain();
	}
}
