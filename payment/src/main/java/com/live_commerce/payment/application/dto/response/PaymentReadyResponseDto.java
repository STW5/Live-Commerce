package com.live_commerce.payment.application.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.live_commerce.payment.application.port.out.PaymentGatewayPort;
import com.live_commerce.payment.infrastructure.client.dto.KakaoPayReadyDto;

public record PaymentReadyResponseDto(
	String tid,
	@JsonProperty("next_redirect_pc_url")
	String nextRedirectUrl
) {
	// Port 추상화 레벨에서 변환
	public static PaymentReadyResponseDto from(PaymentGatewayPort.PaymentReadyResult result) {
		return new PaymentReadyResponseDto(result.tid(), result.redirectUrl());
	}

	// 기존 Infrastructure DTO 호환성 유지 (임시)
	public static PaymentReadyResponseDto from(KakaoPayReadyDto dto) {
		return new PaymentReadyResponseDto(dto.tid(), dto.nextRedirectPcUrl());
	}
}
