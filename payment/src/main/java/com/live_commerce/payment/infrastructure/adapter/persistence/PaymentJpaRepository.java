package com.live_commerce.payment.infrastructure.adapter.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.live_commerce.payment.domain.model.PaymentStatus;

/**
 * 결제 JPA Repository - Adapter Layer 전용
 * {@code domain/repository/PaymentRepository}를 대체
 */
public interface PaymentJpaRepository
	extends JpaRepository<PaymentJpaEntity, UUID>, PaymentQueryJpaRepository {

	Optional<PaymentJpaEntity> findByOrderId(UUID orderId);

	Optional<PaymentJpaEntity> findByOrderIdAndStatus(UUID orderId, PaymentStatus paymentStatus);
}
