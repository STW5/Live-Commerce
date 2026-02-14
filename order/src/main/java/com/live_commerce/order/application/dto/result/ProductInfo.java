package com.live_commerce.order.application.dto.result;

import com.live_commerce.order.domain.model.vo.Money;

import java.util.UUID;

/**
 * 상품 서비스 조회 결과 (Port 응답용 내부 DTO)
 * - ProductQueryPort.findById() 반환 타입
 * - Feign 전용 DTO와 분리: 어댑터가 Feign DTO → ProductInfo 변환 책임
 */
public record ProductInfo(
        UUID productId,
        String name,
        Money unitPrice
) {}
