package com.live_commerce.order.domain.port.out;

import com.live_commerce.order.application.dto.result.ProductInfo;

import java.util.UUID;

/**
 * 상품 서비스 조회 포트 (Outbound)
 * - 구현체: adapter/out/client/ProductFeignAdapter
 */
public interface ProductQueryPort {
    /**
     * 상품 정보 조회 (없으면 null 반환)
     */
    ProductInfo findById(UUID productId);

    /**
     * 주문 가능한 재고가 있는지 확인
     */
    boolean isOrderable(UUID productId, int quantity);
}
