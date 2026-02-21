package com.live_commerce.payment.domain.model;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import com.live_commerce.payment.domain.event.PaymentCompletedDomainEvent;
import com.live_commerce.payment.domain.event.PaymentDomainEvent;
import com.live_commerce.payment.domain.event.PaymentFailedDomainEvent;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 결제 Aggregate Root - 순수 Java 도메인 모델 (JPA 의존 없음)
 * - 결제 도메인의 중심 엔티티
 * - 모든 비즈니스 규칙과 상태 전이를 관리
 * - 도메인 이벤트를 발생시키는 책임을 가짐
 * - JPA 엔티티: {@code infrastructure.adapter.persistence.PaymentJpaEntity}
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment {

	private final List<PaymentDomainEvent> domainEvents = new ArrayList<>();

	private UUID id;
	private UUID orderId;
	private UUID userId;
	private BigDecimal amount;
	private PaymentStatus status;
	private String tid;

	public void assignTid(String tid) {
		if (tid == null || tid.isBlank()) {
			throw new IllegalArgumentException("TID는 비어있을 수 없습니다");
		}
		this.tid = tid;
	}

	// 도메인 비즈니스 메서드 - 상태 전이 규칙 포함
	public void complete() {
		validateStatusTransition(PaymentStatus.COMPLETED);
		this.status = PaymentStatus.COMPLETED;
		registerEvent(PaymentCompletedDomainEvent.of(this.orderId, this.id, this.amount));
	}

	public void fail() {
		validateStatusTransition(PaymentStatus.FAILED);
		this.status = PaymentStatus.FAILED;
		registerEvent(PaymentFailedDomainEvent.of(this.orderId, this.id, "결제 실패"));
	}

	public void failWithReason(String reason) {
		validateStatusTransition(PaymentStatus.FAILED);
		this.status = PaymentStatus.FAILED;
		registerEvent(PaymentFailedDomainEvent.of(this.orderId, this.id, reason));
	}

	public void refund() {
		if (this.status != PaymentStatus.COMPLETED) {
			throw new IllegalStateException(
				String.format("완료된 결제만 환불 가능합니다. 현재 상태: %s", this.status)
			);
		}
		this.status = PaymentStatus.REFUND;
	}

	public void cancel() {
		if (this.status != PaymentStatus.PENDING) {
			throw new IllegalStateException(
				String.format("대기 중인 결제만 취소 가능합니다. 현재 상태: %s", this.status)
			);
		}
		this.status = PaymentStatus.CANCELED;
	}

	// 도메인 규칙 검증
	public boolean isPending() {
		return this.status == PaymentStatus.PENDING;
	}

	public boolean isCompleted() {
		return this.status == PaymentStatus.COMPLETED;
	}

	public boolean canRefund() {
		return this.status == PaymentStatus.COMPLETED;
	}

	public boolean belongsToUser(UUID userId) {
		return this.userId.equals(userId);
	}

	// 상태 전이 검증 (내부 메서드)
	private void validateStatusTransition(PaymentStatus targetStatus) {
		if (!canTransitionTo(targetStatus)) {
			throw new IllegalStateException(
				String.format("잘못된 상태 전이: %s -> %s", this.status, targetStatus)
			);
		}
	}

	private boolean canTransitionTo(PaymentStatus targetStatus) {
		return switch (this.status) {
			case PENDING -> targetStatus == PaymentStatus.COMPLETED || targetStatus == PaymentStatus.FAILED
				|| targetStatus == PaymentStatus.CANCELED;
			case COMPLETED -> targetStatus == PaymentStatus.REFUND;
			case FAILED, CANCELED, REFUND -> false;
		};
	}

	// 정적 팩토리 메서드 - 신규 결제 생성
	public static Payment of(UUID userId, UUID orderId, BigDecimal amount) {
		if (userId == null || orderId == null) {
			throw new IllegalArgumentException("userId와 orderId는 필수입니다");
		}
		if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
			throw new IllegalArgumentException("결제 금액은 0보다 커야 합니다");
		}
		return new Payment(userId, orderId, amount, PaymentStatus.PENDING);
	}

	// 정적 팩토리 메서드 - 저장된 결제 복원 (Adapter Layer에서 호출)
	public static Payment reconstitute(UUID id, UUID orderId, UUID userId, BigDecimal amount,
		PaymentStatus status, String tid) {
		Payment payment = new Payment(userId, orderId, amount, status);
		payment.id = id;
		payment.tid = tid;
		return payment;
	}

	// 프라이빗 생성자
	private Payment(UUID userId, UUID orderId, BigDecimal amount, PaymentStatus status) {
		this.userId = userId;
		this.orderId = orderId;
		this.amount = amount;
		this.status = status;
	}

	// 도메인 이벤트 관리 (Aggregate Root 책임)
	private void registerEvent(PaymentDomainEvent event) {
		this.domainEvents.add(event);
	}

	public List<PaymentDomainEvent> getDomainEvents() {
		return Collections.unmodifiableList(domainEvents);
	}

	public void clearDomainEvents() {
		this.domainEvents.clear();
	}
}
