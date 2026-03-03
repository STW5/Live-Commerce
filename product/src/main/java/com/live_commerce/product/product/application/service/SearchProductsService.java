package com.live_commerce.product.product.application.service;

import com.live_commerce.product.product.application.dto.ProductSearchCondition;
import com.live_commerce.product.product.application.dto.result.ProductPageResult;
import com.live_commerce.product.product.application.dto.result.ProductResult;
import com.live_commerce.product.product.domain.port.in.SearchProductsUseCase;
import com.live_commerce.product.product.domain.port.out.InventoryQueryPort;
import com.live_commerce.product.product.domain.port.out.ProductQueryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SearchProductsService implements SearchProductsUseCase {

    private final ProductQueryPort productQueryPort;
    private final InventoryQueryPort inventoryQueryPort;

    private static final List<Integer> ALLOWED_PAGE_SIZES = List.of(10, 30, 50);

    @Transactional(readOnly = true)
    @Override
    public ProductPageResult searchProducts(ProductSearchCondition condition, Pageable pageable) {
        Pageable adjustedPageable = adjustPageable(pageable);

        Page<com.live_commerce.product.product.domain.model.Product> productPage =
                productQueryPort.search(condition, adjustedPageable);

        List<ProductResult> resultList = productPage.getContent().stream()
                .map(product -> {
                    boolean soldOut = inventoryQueryPort.isSoldOut(product.getProductId());
                    return ProductResult.from(product, soldOut);
                })
                .toList();

        Page<ProductResult> page = new PageImpl<>(resultList, adjustedPageable, productPage.getTotalElements());
        return ProductPageResult.from(page);
    }

    private Pageable adjustPageable(Pageable pageable) {
        int size = ALLOWED_PAGE_SIZES.contains(pageable.getPageSize()) ? pageable.getPageSize() : 10;
        return PageRequest.of(pageable.getPageNumber(), size, pageable.getSort());
    }
}
