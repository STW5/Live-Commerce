package com.live_commerce.payment.application.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.live_commerce.payment.application.dto.response.PaymentApproveResponseDto;
import com.live_commerce.payment.application.exception.CustomException;
import com.live_commerce.payment.application.exception.KakaoPayApiException;
import com.live_commerce.payment.application.exception.PaymentExceptionCode;
import com.live_commerce.payment.application.port.in.ApprovePaymentUseCase;
import com.live_commerce.payment.application.port.out.LoadPaymentPort;
import com.live_commerce.payment.application.port.out.PaymentGatewayPort;
import com.live_commerce.payment.application.port.out.PublishPaymentEventPort;
import com.live_commerce.payment.application.port.out.SavePaymentPort;
import com.live_commerce.payment.domain.model.Payment;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 결제 승인 서비스
 * - 단일 책임: 결제 승인 유스케이스만 처리
 * - 트랜잭션 경계 관리
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApprovePaymentService implements ApprovePaymentUseCase {

	private final LoadPaymentPort loadPaymentPort;
	private final SavePaymentPort savePaymentPort;
	private final PaymentGatewayPort paymentGatewayPort;
	private final PublishPaymentEventPort publishPaymentEventPort;

	@Override
	@Transactional
	public PaymentApproveResponseDto approve(ApprovePaymentCommand command) {
		Payment payment = loadPaymentPort.loadByOrderId(command.orderId())
			.orElseThrow(() -> new CustomException(PaymentExceptionCode.NOT_FOUND));

		// 도메인 규칙 검증
		if (!payment.isPending()) {
			throw new CustomException(PaymentExceptionCode.INVALID_STATUS);
		}

		// 외부 결제 게이트웨이 호출
		PaymentGatewayPort.PaymentApproveResult approveResult;
		try {
			approveResult = paymentGatewayPort.approve(
				command.tid(),
				command.pgToken(),
				command.orderId(),
				command.userId()
			);
		} catch (KakaoPayApiException e) {
			log.warn("[Payment] 결제 승인 실패 - orderId: {}, 사유: {}", command.orderId(), e.getMessage());
			handlePaymentFailed(payment, e.getMessage());
			throw new CustomException(PaymentExceptionCode.PAYMENT_APPROVE_FAIL);
		}

		// 결제 완료 처리
		payment.complete();
		savePaymentPort.save(payment);

		// 결제 완료 이벤트 발행
		publishPaymentEventPort.publishCompleted(
			new PublishPaymentEventPort.PaymentCompletedEvent(
				payment.getOrderId(),
				"결제 완료",
				payment.getAmount()
			)
		);

		log.info("[Payment] 결제 승인 완료 - orderId: {}, amount: {}", payment.getOrderId(), payment.getAmount());

		return PaymentApproveResponseDto.from(approveResult);
	}

	private void handlePaymentFailed(Payment payment, String reason) {
		payment.fail();
		savePaymentPort.save(payment);

		publishPaymentEventPort.publishFailed(
			new PublishPaymentEventPort.PaymentFailedEvent(
				payment.getOrderId(),
				"카카오페이 승인 실패: " + reason
			)
		);
	}
}
