package com.live_commerce.product.product.domain.port.in;

import java.util.UUID;

public interface DeleteProductUseCase {
    void deleteProduct(UUID productId, UUID requestUserId, String requestUserRole, UUID requestUserCompanyId);
}
