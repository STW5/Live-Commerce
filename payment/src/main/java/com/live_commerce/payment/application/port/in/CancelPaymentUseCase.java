package com.live_commerce.payment.application.port.in;

import java.util.UUID;

/**
 * 결제 취소 유스케이스
 * - PENDING 상태의 결제를 CANCELED 상태로 전환
 * - 소유자 또는 MASTER 권한 필요
 */
public interface CancelPaymentUseCase {
	void cancel(CancelPaymentCommand command);

	/**
	 * 결제 취소 커맨드
	 */
	record CancelPaymentCommand(
		UUID orderId,
		UUID userId,
		boolean hasMasterRole
	) {
		public CancelPaymentCommand {
			if (orderId == null || userId == null) {
				throw new IllegalArgumentException("orderId와 userId는 필수입니다");
			}
		}
	}
}
