package com.live_commerce.product.product.domain.port.in;

import com.live_commerce.product.product.application.dto.command.CreateProductCommand;
import com.live_commerce.product.product.application.dto.result.ProductResult;

public interface CreateProductUseCase {
    ProductResult createProduct(CreateProductCommand command);
}
