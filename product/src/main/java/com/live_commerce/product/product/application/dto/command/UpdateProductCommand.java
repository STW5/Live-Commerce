package com.live_commerce.product.product.application.dto.command;

import com.live_commerce.product.product.domain.model.ProductCategory;
import com.live_commerce.product.product.domain.model.ProductStatus;

import java.util.UUID;

public record UpdateProductCommand(
        UUID productId,
        String name,
        String description,
        Integer price,
        ProductCategory category,
        ProductStatus productStatus,
        UUID requestUserId,
        String requestUserRole,
        UUID requestUserCompanyId
) {}
