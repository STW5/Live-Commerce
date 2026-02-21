package com.live_commerce.payment.application.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.live_commerce.payment.application.exception.CustomException;
import com.live_commerce.payment.application.exception.KakaoPayApiException;
import com.live_commerce.payment.application.exception.PaymentExceptionCode;
import com.live_commerce.payment.application.port.in.CompensatePaymentUseCase;
import com.live_commerce.payment.application.port.out.LoadPaymentPort;
import com.live_commerce.payment.application.port.out.PaymentGatewayPort;
import com.live_commerce.payment.application.port.out.SavePaymentPort;
import com.live_commerce.payment.domain.model.Payment;
import com.live_commerce.payment.domain.model.PaymentStatus;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 결제 보상 트랜잭션 서비스
 * - 단일 책임: 보상 트랜잭션 유스케이스만 처리
 * - PaymentServiceV2.compensateRefundByOrderId() 로직 이관
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CompensatePaymentService implements CompensatePaymentUseCase {

	private final LoadPaymentPort loadPaymentPort;
	private final SavePaymentPort savePaymentPort;
	private final PaymentGatewayPort paymentGatewayPort;

	@Override
	@Transactional
	public void compensate(CompensatePaymentCommand command) {
		Payment payment = loadPaymentPort.loadByOrderId(command.orderId())
			.orElseThrow(() -> new CustomException(PaymentExceptionCode.NOT_FOUND));

		// 이미 환불/취소된 경우 스킵
		if (payment.getStatus() != PaymentStatus.COMPLETED) {
			log.info("[Payment] 보상 처리 스킵: 이미 취소/실패한 결제. orderId={}, status={}",
				command.orderId(), payment.getStatus());
			return;
		}

		// 카카오페이 환불 처리
		try {
			paymentGatewayPort.cancel(payment.getTid(), payment.getAmount());
		} catch (KakaoPayApiException e) {
			log.error("[Payment] 보상 환불 실패 - orderId: {}, 사유: {}", command.orderId(), e.getMessage());
			throw e;
		}

		// 환불 처리
		payment.refund();
		savePaymentPort.save(payment);

		log.info("[Payment] 보상 결제 취소 완료: orderId={}, reason={}", command.orderId(), command.reason());
	}
}
