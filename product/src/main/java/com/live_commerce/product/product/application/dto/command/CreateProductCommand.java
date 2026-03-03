package com.live_commerce.product.product.application.dto.command;

import com.live_commerce.product.product.domain.model.ProductCategory;

import java.util.UUID;

public record CreateProductCommand(
        UUID companyId,
        String name,
        String description,
        Integer price,
        ProductCategory category,
        UUID requestUserId,
        String requestUserRole,
        UUID requestUserCompanyId
) {}
