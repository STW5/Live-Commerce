package com.live_commerce.payment.application.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import com.live_commerce.payment.application.port.out.PaymentGatewayPort;
import com.live_commerce.payment.infrastructure.client.dto.KakaoPayApproveDto;

public record PaymentApproveResponseDto(
	String tid,
	java.time.LocalDateTime approvedAt,
	BigDecimal amount
) {
	// Port 추상화 레벨에서 변환
	public static PaymentApproveResponseDto from(PaymentGatewayPort.PaymentApproveResult result) {
		return new PaymentApproveResponseDto(
			result.tid(),
			LocalDateTime.parse(result.approvedAt(), DateTimeFormatter.ISO_LOCAL_DATE_TIME),
			BigDecimal.valueOf(result.amount().total())
		);
	}

	// 기존 Infrastructure DTO 호환성 유지 (임시)
	public static PaymentApproveResponseDto from(KakaoPayApproveDto dto) {
		return new PaymentApproveResponseDto(
			dto.tid(),
			LocalDateTime.parse(dto.approvedAt(), DateTimeFormatter.ISO_LOCAL_DATE_TIME),
			BigDecimal.valueOf(dto.amount().total())
		);
	}
}

