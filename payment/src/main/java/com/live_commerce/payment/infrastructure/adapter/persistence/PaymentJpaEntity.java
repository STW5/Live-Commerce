package com.live_commerce.payment.infrastructure.adapter.persistence;

import java.math.BigDecimal;
import java.util.UUID;

import org.hibernate.annotations.UuidGenerator;

import com.live_commerce.payment.domain.model.Payment;
import com.live_commerce.payment.domain.model.PaymentStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 결제 JPA 엔티티 - Adapter Layer 전용
 * Domain Payment 모델과 분리: JPA 의존 제거를 위해 신규 생성
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "p_payment")
public class PaymentJpaEntity extends BaseJpaEntity {

	@Id
	@UuidGenerator
	private UUID id;

	@Column(nullable = false, unique = true)
	private UUID orderId;

	@Column(nullable = false)
	private UUID userId;

	@Column(nullable = false, precision = 10, scale = 2)
	private BigDecimal amount;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private PaymentStatus status;

	private String tid;

	// 도메인 모델 → JPA 엔티티 변환
	public static PaymentJpaEntity from(Payment domain) {
		PaymentJpaEntity entity = new PaymentJpaEntity();
		entity.id = domain.getId();
		entity.orderId = domain.getOrderId();
		entity.userId = domain.getUserId();
		entity.amount = domain.getAmount();
		entity.status = domain.getStatus();
		entity.tid = domain.getTid();
		return entity;
	}

	// JPA 엔티티 → 도메인 모델 변환
	public Payment toDomain() {
		return Payment.reconstitute(
			this.id,
			this.orderId,
			this.userId,
			this.amount,
			this.status,
			this.tid
		);
	}
}
