package com.live_commerce.payment.infrastructure.client;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import com.live_commerce.payment.application.exception.KakaoPayApiException;
import com.live_commerce.payment.infrastructure.client.dto.KakaoPayApproveDto;
import com.live_commerce.payment.infrastructure.client.dto.KakaoPayCancelDto;
import com.live_commerce.payment.infrastructure.client.dto.KakaoPayReadyDto;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class KakaoPayClientImpl {

	private final RestTemplate restTemplate;
	private final RetryTemplate retryTemplate;

	@Value("${gateway.base-url}")
	private String gatewayBaseUrl;

	@Value("${kakao.pay.secret-key}")
	private String kakaoPaySecretKey;

	@Value("${kakao.pay.cid}")
	private String kakaoPayCid;

	public KakaoPayReadyDto requestKakaoPayReady(UUID userId, UUID orderId, BigDecimal amount, String itemName) {
		Map<String, Object> params = new HashMap<>();
		params.put("cid", kakaoPayCid);
		params.put("partner_order_id", orderId.toString());
		params.put("partner_user_id", userId.toString());
		params.put("item_name", itemName);
		params.put("quantity", 1);
		params.put("total_amount", amount.intValue());
		params.put("tax_free_amount", 0);
		params.put("approval_url", gatewayBaseUrl + "/api/v1/payments/approve");
		params.put("cancel_url", gatewayBaseUrl + "/api/v1/payments/cancel");
		params.put("fail_url", gatewayBaseUrl + "/api/v1/payments/fail");

		HttpHeaders headers = new HttpHeaders();
		headers.set("Authorization", "SECRET_KEY " + kakaoPaySecretKey);
		headers.setContentType(MediaType.APPLICATION_JSON);

		HttpEntity<Map<String, Object>> request = new HttpEntity<>(params, headers);

		try {
			return retryTemplate.execute(context -> {
				ResponseEntity<KakaoPayReadyDto> response = restTemplate.postForEntity(
					"https://open-api.kakaopay.com/online/v1/payment/ready",
					request,
					KakaoPayReadyDto.class
				);
				return response.getBody();
			});
		} catch (Exception e) {
			throw KakaoPayApiException.forReadyFailed(e);
		}
	}

	public KakaoPayApproveDto requestKakaoPayApprove(String tid, String pgToken, String orderId, String userId) {
		Map<String, Object> params = new HashMap<>();
		params.put("cid", kakaoPayCid);
		params.put("tid", tid);
		params.put("partner_order_id", orderId);
		params.put("partner_user_id", userId);
		params.put("pg_token", pgToken);

		HttpHeaders headers = new HttpHeaders();
		headers.set("Authorization", "SECRET_KEY " + kakaoPaySecretKey);
		headers.setContentType(MediaType.APPLICATION_JSON);

		HttpEntity<Map<String, Object>> request = new HttpEntity<>(params, headers);

		try {
			return retryTemplate.execute(context -> {
				ResponseEntity<KakaoPayApproveDto> response = restTemplate.postForEntity(
					"https://open-api.kakaopay.com/online/v1/payment/approve",
					request,
					KakaoPayApproveDto.class
				);
				return response.getBody();
			});
		} catch (Exception e) {
			throw KakaoPayApiException.forApproveFailed(e);
		}
	}

	public KakaoPayCancelDto requestKakaoPayCancel(String tid, BigDecimal cancelAmount) {
		Map<String, Object> params = new HashMap<>();
		params.put("cid", kakaoPayCid);
		params.put("tid", tid);
		params.put("cancel_amount", cancelAmount.intValue());
		params.put("cancel_tax_free_amount", 0);

		HttpHeaders headers = new HttpHeaders();
		headers.set("Authorization", "SECRET_KEY " + kakaoPaySecretKey);
		headers.setContentType(MediaType.APPLICATION_JSON);

		HttpEntity<Map<String, Object>> request = new HttpEntity<>(params, headers);

		try {
			return retryTemplate.execute(context -> {
				ResponseEntity<KakaoPayCancelDto> response = restTemplate.postForEntity(
					"https://open-api.kakaopay.com/online/v1/payment/cancel",
					request,
					KakaoPayCancelDto.class
				);
				return response.getBody();
			});
		} catch (Exception e) {
			throw KakaoPayApiException.forCancelFailed(e);
		}
	}
}
