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
import com.live_commerce.payment.domain.repository.PaymentRepository;

import lombok.RequiredArgsConstructor;

/**
 * 결제 영속성 어댑터
 * - LoadPaymentPort, SavePaymentPort 구현
 * - JPA Repository를 활용한 데이터 접근
 */
@Component
@RequiredArgsConstructor
public class PaymentPersistenceAdapter implements LoadPaymentPort, SavePaymentPort {

	private final PaymentRepository paymentRepository;

	@Override
	public Optional<Payment> loadById(UUID paymentId) {
		return paymentRepository.findById(paymentId);
	}

	@Override
	public Optional<Payment> loadByOrderId(UUID orderId) {
		return paymentRepository.findByOrderId(orderId);
	}

	@Override
	public List<Payment> search(PaymentSearchCondition condition, Pageable pageable) {
		return paymentRepository.searchPayment(condition, pageable);
	}

	@Override
	public long count(PaymentSearchCondition condition) {
		return paymentRepository.countPayment(condition);
	}

	@Override
	public Payment save(Payment payment) {
		return paymentRepository.save(payment);
	}
}
