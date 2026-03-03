package com.live_commerce.product.product.domain.port.out;

import java.util.UUID;

/**
 * product 서브도메인에서 inventory 상태를 조회하기 위한 cross-subdomain query port
 */
public interface InventoryQueryPort {
    boolean isSoldOut(UUID productId);
}
