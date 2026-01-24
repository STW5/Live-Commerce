package com.live_commerce.payment.infrastructure.adapter.gateway;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.live_commerce.payment.application.port.KakaoPayClient;
import com.live_commerce.payment.application.port.out.PaymentGatewayPort;
import com.live_commerce.payment.infrastructure.client.dto.KakaoPayApproveDto;
import com.live_commerce.payment.infrastructure.client.dto.KakaoPayReadyDto;

import lombok.RequiredArgsConstructor;

/**
 * 카카오페이 게이트웨이 어댑터
 * - PaymentGatewayPort 구현
 * - KakaoPayClient(기존 구현)를 활용하여 외부 API 호출
 * - Infrastructure DTO → Port DTO 변환
 */
@Component
@RequiredArgsConstructor
public class KakaoPayGatewayAdapter implements PaymentGatewayPort {

	private final KakaoPayClient kakaoPayClient;

	@Override
	public PaymentReadyResult ready(UUID userId, UUID orderId, BigDecimal amount, String itemName) {
		KakaoPayReadyDto dto = kakaoPayClient.requestKakaoPayReady(userId, orderId, amount, itemName);
		return new PaymentReadyResult(
			dto.tid(),
			dto.nextRedirectPcUrl(),
			dto.createdAt()
		);
	}

	@Override
	public PaymentApproveResult approve(String tid, String pgToken, UUID orderId, UUID userId) {
		KakaoPayApproveDto dto = kakaoPayClient.requestKakaoPayApprove(
			tid,
			pgToken,
			orderId.toString(),
			userId.toString()
		);

		return new PaymentApproveResult(
			dto.aid(),
			dto.tid(),
			dto.cid(),
			dto.partnerOrderId(),
			dto.partnerUserId(),
			dto.paymentMethodType(),
			dto.itemName(),
			dto.quantity(),
			dto.createdAt(),
			dto.approvedAt(),
			new PaymentAmount(
				dto.amount().total(),
				dto.amount().taxFree(),
				dto.amount().vat(),
				dto.amount().point(),
				dto.amount().discount()
			)
		);
	}

	@Override
	public void cancel(String tid, BigDecimal amount) {
		kakaoPayClient.requestKakaoPayCancel(tid, amount);
	}
}
