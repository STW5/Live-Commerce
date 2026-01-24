package com.live_commerce.payment.domain.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 결제 완료 도메인 이벤트
 * - 결제가 성공적으로 완료되었을 때 발생
 */
public record PaymentCompletedDomainEvent(
	UUID eventId,
	UUID orderId,
	UUID paymentId,
	BigDecimal amount,
	LocalDateTime occurredOn
) implements PaymentDomainEvent {

	public static PaymentCompletedDomainEvent of(UUID orderId, UUID paymentId, BigDecimal amount) {
		return new PaymentCompletedDomainEvent(
			UUID.randomUUID(),
			orderId,
			paymentId,
			amount,
			LocalDateTime.now()
		);
	}

	@Override
	public String eventType() {
		return "PaymentCompleted";
	}
}
