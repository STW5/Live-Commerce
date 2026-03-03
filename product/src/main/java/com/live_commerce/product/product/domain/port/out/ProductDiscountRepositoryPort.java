package com.live_commerce.product.product.domain.port.out;

import com.live_commerce.product.product.domain.model.ProductDiscount;

public interface ProductDiscountRepositoryPort {
    ProductDiscount save(ProductDiscount productDiscount);
}
