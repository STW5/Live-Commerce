package com.live_commerce.product.product.domain.port.in;

import com.live_commerce.product.product.application.dto.command.UpdateProductCommand;
import com.live_commerce.product.product.application.dto.result.ProductResult;

public interface UpdateProductUseCase {
    ProductResult updateProduct(UpdateProductCommand command);
}
