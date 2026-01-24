package com.live_commerce.payment.domain.event;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 결제 도메인 이벤트 (부모 인터페이스)
 * - 도메인 레벨에서 발생하는 모든 결제 이벤트의 공통 인터페이스
 */
public interface PaymentDomainEvent {
	UUID eventId();

	UUID orderId();

	LocalDateTime occurredOn();

	String eventType();
}
