package com.live_commerce.payment.presentation.controller;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.live_commerce.payment.application.dto.request.PaymentApproveRequestDto;
import com.live_commerce.payment.application.dto.request.PaymentReadyRequestDto;
import com.live_commerce.payment.application.dto.request.PaymentRefundResponseDto;
import com.live_commerce.payment.application.dto.request.PaymentSearchCondition;
import com.live_commerce.payment.application.dto.response.PaymentApproveResponseDto;
import com.live_commerce.payment.application.dto.response.PaymentGetResponseDto;
import com.live_commerce.payment.application.dto.response.PaymentReadyResponseDto;
import com.live_commerce.payment.application.port.in.ApprovePaymentUseCase;
import com.live_commerce.payment.application.port.in.ApprovePaymentUseCase.ApprovePaymentCommand;
import com.live_commerce.payment.application.port.in.CancelPaymentUseCase;
import com.live_commerce.payment.application.port.in.CancelPaymentUseCase.CancelPaymentCommand;
import com.live_commerce.payment.application.port.in.GetPaymentUseCase;
import com.live_commerce.payment.application.port.in.GetPaymentUseCase.GetPaymentQuery;
import com.live_commerce.payment.application.port.in.GetPaymentUseCase.SearchPaymentQuery;
import com.live_commerce.payment.application.port.in.ReadyPaymentUseCase;
import com.live_commerce.payment.application.port.in.ReadyPaymentUseCase.ReadyPaymentCommand;
import com.live_commerce.payment.application.port.in.RefundPaymentUseCase;
import com.live_commerce.payment.application.port.in.RefundPaymentUseCase.RefundPaymentCommand;
import com.live_commerce.payment.infrastructure.common.ResponseUtil;
import com.live_commerce.payment.infrastructure.security.RequestUserDetails;
import com.live_commerce.payment.presentation.common.ApiResponse;

import lombok.RequiredArgsConstructor;

/**
 * 결제 컨트롤러 V3 - Hexagonal Architecture 기반
 * Use Case 인터페이스를 직접 호출하는 Presentation Layer 어댑터
 */
@RestController
@RequestMapping("/api/v3/payments")
@RequiredArgsConstructor
public class PaymentControllerV3 {

	private final ReadyPaymentUseCase readyPaymentUseCase;
	private final ApprovePaymentUseCase approvePaymentUseCase;
	private final RefundPaymentUseCase refundPaymentUseCase;
	private final GetPaymentUseCase getPaymentUseCase;
	private final CancelPaymentUseCase cancelPaymentUseCase;

	@PostMapping("/ready")
	public ResponseEntity<ApiResponse<PaymentReadyResponseDto>> readyPayment(
		@AuthenticationPrincipal RequestUserDetails userDetails,
		@RequestBody PaymentReadyRequestDto requestDto
	) {
		PaymentReadyResponseDto response = readyPaymentUseCase.ready(
			new ReadyPaymentCommand(
				userDetails.getUserId(),
				requestDto.orderId(),
				requestDto.amount(),
				requestDto.itemName()
			)
		);
		return ResponseUtil.success(response);
	}

	@PostMapping("/approve")
	public ResponseEntity<ApiResponse<PaymentApproveResponseDto>> approvePayment(
		@AuthenticationPrincipal RequestUserDetails userDetails,
		@RequestBody PaymentApproveRequestDto requestDto
	) {
		PaymentApproveResponseDto response = approvePaymentUseCase.approve(
			new ApprovePaymentCommand(
				userDetails.getUserId(),
				UUID.fromString(requestDto.orderId()),
				requestDto.tid(),
				requestDto.pgToken()
			)
		);
		return ResponseUtil.success(response);
	}

	@GetMapping("/{paymentId}")
	public ResponseEntity<ApiResponse<PaymentGetResponseDto>> getPayment(
		@PathVariable UUID paymentId,
		@AuthenticationPrincipal RequestUserDetails userDetails
	) {
		PaymentGetResponseDto response = getPaymentUseCase.getById(
			new GetPaymentQuery(
				paymentId,
				userDetails.getUserId(),
				hasMasterRole(userDetails)
			)
		);
		return ResponseUtil.success(response);
	}

	@GetMapping
	public ResponseEntity<ApiResponse<Page<PaymentGetResponseDto>>> getPayments(
		@ModelAttribute PaymentSearchCondition condition,
		@AuthenticationPrincipal RequestUserDetails userDetails,
		@PageableDefault(size = 10) Pageable pageable
	) {
		Page<PaymentGetResponseDto> result = getPaymentUseCase.search(
			new SearchPaymentQuery(
				condition,
				userDetails.getUserId(),
				hasMasterRole(userDetails)
			),
			pageable
		);
		return ResponseUtil.success(result);
	}

	@PostMapping("/{orderId}/refund")
	public ResponseEntity<ApiResponse<PaymentRefundResponseDto>> refundPayment(
		@PathVariable UUID orderId,
		@AuthenticationPrincipal RequestUserDetails userDetails
	) {
		PaymentRefundResponseDto response = refundPaymentUseCase.refund(
			new RefundPaymentCommand(
				orderId,
				userDetails.getUserId(),
				hasMasterRole(userDetails)
			)
		);
		return ResponseUtil.success(response);
	}

	@PostMapping("/{orderId}/cancel")
	public ResponseEntity<ApiResponse<Void>> cancelPayment(
		@PathVariable UUID orderId,
		@AuthenticationPrincipal RequestUserDetails userDetails
	) {
		cancelPaymentUseCase.cancel(
			new CancelPaymentCommand(
				orderId,
				userDetails.getUserId(),
				hasMasterRole(userDetails)
			)
		);
		return ResponseUtil.noContent();
	}

	private boolean hasMasterRole(RequestUserDetails userDetails) {
		return userDetails.getAuthorities().stream()
			.anyMatch(auth -> auth.getAuthority().equals("ROLE_MASTER"));
	}
}
