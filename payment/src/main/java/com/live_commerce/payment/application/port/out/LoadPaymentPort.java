package com.live_commerce.payment.application.port.out;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;

import com.live_commerce.payment.application.dto.request.PaymentSearchCondition;
import com.live_commerce.payment.domain.model.Payment;

/**
 * 결제 조회 포트 (Outbound)
 */
public interface LoadPaymentPort {
	Optional<Payment> loadById(UUID paymentId);

	Optional<Payment> loadByOrderId(UUID orderId);

	List<Payment> search(PaymentSearchCondition condition, Pageable pageable);

	long count(PaymentSearchCondition condition);
}
