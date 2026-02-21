package com.live_commerce.payment.application.port.in;

import java.util.UUID;

/**
 * 결제 보상 트랜잭션 유스케이스
 * - 주문 실패 시 결제 환불 처리
 */
public interface CompensatePaymentUseCase {
	void compensate(CompensatePaymentCommand command);

	/**
	 * 보상 커맨드
	 */
	record CompensatePaymentCommand(UUID orderId, String reason) {
		public CompensatePaymentCommand {
			if (orderId == null) {
				throw new IllegalArgumentException("orderId는 필수입니다");
			}
		}
	}
}
