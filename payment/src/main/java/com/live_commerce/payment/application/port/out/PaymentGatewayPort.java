package com.live_commerce.payment.application.port.out;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * 결제 게이트웨이 포트 (Outbound)
 * - KakaoPay 등 외부 결제 시스템 추상화
 */
public interface PaymentGatewayPort {
	PaymentReadyResult ready(UUID userId, UUID orderId, BigDecimal amount, String itemName);

	PaymentApproveResult approve(String tid, String pgToken, UUID orderId, UUID userId);

	void cancel(String tid, BigDecimal amount);

	/**
	 * 결제 준비 결과
	 */
	record PaymentReadyResult(
		String tid,
		String redirectUrl,
		String createdAt
	) {
	}

	/**
	 * 결제 승인 결과
	 */
	record PaymentApproveResult(
		String aid,
		String tid,
		String cid,
		String partnerOrderId,
		String partnerUserId,
		String paymentMethodType,
		String itemName,
		int quantity,
		String createdAt,
		String approvedAt,
		PaymentAmount amount
	) {
	}

	record PaymentAmount(
		int total,
		int taxFree,
		int vat,
		int point,
		int discount
	) {
	}
}
