package com.live_commerce.payment.application.port.out;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * 결제 이벤트 발행 포트 (Outbound)
 */
public interface PublishPaymentEventPort {
	void publishCompleted(PaymentCompletedEvent event);

	void publishFailed(PaymentFailedEvent event);

	/**
	 * 결제 완료 이벤트
	 */
	record PaymentCompletedEvent(
		UUID orderId,
		String message,
		BigDecimal amount
	) {
	}

	/**
	 * 결제 실패 이벤트
	 */
	record PaymentFailedEvent(
		UUID orderId,
		String message
	) {
	}
}
