package com.live_commerce.payment.domain.event;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 결제 실패 도메인 이벤트
 * - 결제가 실패했을 때 발생
 */
public record PaymentFailedDomainEvent(
	UUID eventId,
	UUID orderId,
	UUID paymentId,
	String failureReason,
	LocalDateTime occurredOn
) implements PaymentDomainEvent {

	public static PaymentFailedDomainEvent of(UUID orderId, UUID paymentId, String failureReason) {
		return new PaymentFailedDomainEvent(
			UUID.randomUUID(),
			orderId,
			paymentId,
			failureReason,
			LocalDateTime.now()
		);
	}

	@Override
	public String eventType() {
		return "PaymentFailed";
	}
}
