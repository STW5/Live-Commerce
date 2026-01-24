package com.live_commerce.payment.application.port.in;

import java.util.UUID;

import com.live_commerce.payment.application.dto.response.PaymentApproveResponseDto;

/**
 * 결제 승인 유스케이스
 */
public interface ApprovePaymentUseCase {
	PaymentApproveResponseDto approve(ApprovePaymentCommand command);

	/**
	 * 결제 승인 커맨드
	 */
	record ApprovePaymentCommand(
		UUID userId,
		UUID orderId,
		String tid,
		String pgToken
	) {
		public ApprovePaymentCommand {
			if (userId == null || orderId == null) {
				throw new IllegalArgumentException("userId와 orderId는 필수입니다");
			}
			if (tid == null || tid.isBlank()) {
				throw new IllegalArgumentException("tid는 필수입니다");
			}
			if (pgToken == null || pgToken.isBlank()) {
				throw new IllegalArgumentException("pgToken은 필수입니다");
			}
		}
	}
}
