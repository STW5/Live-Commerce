package com.live_commerce.payment.domain.service;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.live_commerce.payment.domain.model.Payment;

/**
 * 결제 도메인 검증 서비스
 * - 권한 검증 로직을 도메인 레이어로 캡슐화
 * - 비즈니스 규칙 검증
 */
@Component
public class PaymentValidator {

	private static final String ROLE_MASTER = "ROLE_MASTER";

	/**
	 * 결제 조회 권한 검증
	 * - 본인 또는 마스터 권한 필요
	 */
	public void validateGetPermission(Payment payment, UUID userId, boolean hasMasterRole) {
		if (!payment.belongsToUser(userId) && !hasMasterRole) {
			throw new IllegalAccessError("결제 조회 권한이 없습니다");
		}
	}

	/**
	 * 결제 환불 권한 검증
	 * - 본인 또는 마스터 권한 필요
	 */
	public void validateRefundPermission(Payment payment, UUID userId, boolean hasMasterRole) {
		if (!payment.belongsToUser(userId) && !hasMasterRole) {
			throw new IllegalAccessError("결제 환불 권한이 없습니다");
		}
	}

	/**
	 * 결제 취소 권한 검증
	 * - 본인 또는 마스터 권한 필요
	 */
	public void validateCancelPermission(Payment payment, UUID userId, boolean hasMasterRole) {
		if (!payment.belongsToUser(userId) && !hasMasterRole) {
			throw new IllegalAccessError("결제 취소 권한이 없습니다");
		}
	}

	/**
	 * 결제 검색 권한 검증
	 * - 마스터 권한 필요 (전체 검색의 경우)
	 */
	public void validateSearchPermission(UUID searchUserId, UUID requestUserId, boolean hasMasterRole) {
		if (searchUserId == null) {
			// 전체 검색은 마스터만 가능
			if (!hasMasterRole) {
				throw new IllegalAccessError("전체 결제 검색 권한이 없습니다");
			}
		} else {
			// 특정 사용자 검색은 본인 또는 마스터만 가능
			if (!searchUserId.equals(requestUserId) && !hasMasterRole) {
				throw new IllegalAccessError("다른 사용자의 결제 검색 권한이 없습니다");
			}
		}
	}
}
