package com.live_commerce.payment.application.port.in;

import java.math.BigDecimal;
import java.util.UUID;

import com.live_commerce.payment.application.dto.response.PaymentReadyResponseDto;

/**
 * 결제 준비 유스케이스
 */
public interface ReadyPaymentUseCase {
	PaymentReadyResponseDto ready(ReadyPaymentCommand command);

	/**
	 * 결제 준비 커맨드
	 */
	record ReadyPaymentCommand(
		UUID userId,
		UUID orderId,
		BigDecimal amount,
		String itemName
	) {
		public ReadyPaymentCommand {
			if (userId == null || orderId == null) {
				throw new IllegalArgumentException("userId와 orderId는 필수입니다");
			}
			if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
				throw new IllegalArgumentException("결제 금액은 0보다 커야 합니다");
			}
			if (itemName == null || itemName.isBlank()) {
				throw new IllegalArgumentException("상품명은 필수입니다");
			}
		}
	}
}
