package com.live_commerce.payment.application.port.in;

import java.util.UUID;

import com.live_commerce.payment.application.dto.request.PaymentRefundResponseDto;

/**
 * 결제 환불 유스케이스
 */
public interface RefundPaymentUseCase {
	PaymentRefundResponseDto refund(RefundPaymentCommand command);

	/**
	 * 결제 환불 커맨드
	 */
	record RefundPaymentCommand(
		UUID orderId,
		UUID userId,
		boolean hasMasterRole
	) {
		public RefundPaymentCommand {
			if (orderId == null || userId == null) {
				throw new IllegalArgumentException("orderId와 userId는 필수입니다");
			}
		}
	}
}
