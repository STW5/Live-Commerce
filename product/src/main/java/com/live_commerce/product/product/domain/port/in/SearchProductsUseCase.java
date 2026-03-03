package com.live_commerce.product.product.domain.port.in;

import com.live_commerce.product.product.application.dto.ProductSearchCondition;
import com.live_commerce.product.product.application.dto.result.ProductPageResult;
import org.springframework.data.domain.Pageable;

public interface SearchProductsUseCase {
    ProductPageResult searchProducts(ProductSearchCondition condition, Pageable pageable);
}
