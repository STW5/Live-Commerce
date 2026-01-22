package com.live_commerce.payment.application.exception;

/**
 * KakaoPay API 호출 실패 시 발생하는 예외
 * - API 응답 오류 (4xx, 5xx)
 * - 잘못된 파라미터
 * - 네트워크 타임아웃
 */
public class KakaoPayApiException extends RuntimeException {

	public KakaoPayApiException(String message) {
		super(message);
	}

	public KakaoPayApiException(String message, Throwable cause) {
		super(message, cause);
	}

	public static KakaoPayApiException forReadyFailed(Throwable cause) {
		return new KakaoPayApiException("카카오페이 결제 준비 실패", cause);
	}

	public static KakaoPayApiException forApproveFailed(Throwable cause) {
		return new KakaoPayApiException("카카오페이 승인 실패", cause);
	}

	public static KakaoPayApiException forCancelFailed(Throwable cause) {
		return new KakaoPayApiException("카카오페이 취소 실패", cause);
	}
}
