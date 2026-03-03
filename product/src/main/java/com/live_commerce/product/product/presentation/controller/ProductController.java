package com.live_commerce.product.product.presentation.controller;

import com.live_commerce.product.product.application.dto.*;
import com.live_commerce.product.product.application.dto.command.CreateProductCommand;
import com.live_commerce.product.product.application.dto.command.UpdateProductCommand;
import com.live_commerce.product.product.application.dto.result.*;
import com.live_commerce.product.product.domain.port.in.*;
import com.live_commerce.product.product.infrastructure.common.ResponseUtil;
import com.live_commerce.product.product.infrastructure.security.RequestUserDetails;
import com.live_commerce.product.product.presentation.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductController {

    private final CreateProductUseCase createProductUseCase;
    private final GetProductUseCase getProductUseCase;
    private final UpdateProductUseCase updateProductUseCase;
    private final DeleteProductUseCase deleteProductUseCase;
    private final SearchProductsUseCase searchProductsUseCase;
    private final GetProductsByIdsUseCase getProductsByIdsUseCase;
    private final GetPopularProductsUseCase getPopularProductsUseCase;
    private final ApplyLiveDiscountUseCase applyLiveDiscountUseCase;
    private final GetProductPriceUseCase getProductPriceUseCase;

    @PreAuthorize("hasAnyRole('MASTER','SELLER')")
    @PostMapping
    public ResponseEntity<ApiResponse<ProductCreateResponseDto>> createProduct(
            @RequestBody ProductCreateRequestDto requestDto,
            @AuthenticationPrincipal RequestUserDetails user
    ) {
        CreateProductCommand command = new CreateProductCommand(
                requestDto.companyId(),
                requestDto.name(),
                requestDto.description(),
                requestDto.price(),
                requestDto.category(),
                user.getUserId(),
                user.getAuthorities().iterator().next().getAuthority(),
                requestDto.companyId()
        );
        ProductResult result = createProductUseCase.createProduct(command);
        return ResponseUtil.success(toCreateResponseDto(result));
    }

    @GetMapping("/{productId}")
    public ResponseEntity<ApiResponse<ProductResponseDto>> getProduct(@PathVariable UUID productId) {
        ProductResult result = getProductUseCase.getProduct(productId);
        return ResponseUtil.success(toResponseDto(result));
    }

    @PreAuthorize("hasAnyRole('MASTER','SELLER')")
    @PatchMapping("/{productId}")
    public ResponseEntity<ApiResponse<ProductResponseDto>> updateProduct(
            @PathVariable UUID productId,
            @RequestBody ProductUpdateRequestDto requestDto,
            @AuthenticationPrincipal RequestUserDetails user
    ) {
        UpdateProductCommand command = new UpdateProductCommand(
                productId,
                requestDto.name(),
                requestDto.description(),
                requestDto.price(),
                requestDto.category(),
                requestDto.productStatus(),
                user.getUserId(),
                user.getAuthorities().iterator().next().getAuthority(),
                null
        );
        ProductResult result = updateProductUseCase.updateProduct(command);
        return ResponseUtil.success(toResponseDto(result));
    }

    @PreAuthorize("hasAnyRole('MASTER','SELLER')")
    @DeleteMapping("/{productId}")
    public ResponseEntity<ApiResponse<String>> deleteProduct(
            @PathVariable UUID productId,
            @AuthenticationPrincipal RequestUserDetails user
    ) {
        deleteProductUseCase.deleteProduct(
                productId,
                user.getUserId(),
                user.getAuthorities().iterator().next().getAuthority(),
                null
        );
        return ResponseUtil.success("상품이 삭제되었습니다.");
    }

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<ProductPageResponseDto>> searchProducts(
            @ModelAttribute ProductSearchCondition condition,
            Pageable pageable
    ) {
        ProductPageResult result = searchProductsUseCase.searchProducts(condition, pageable);
        return ResponseUtil.success(toPageResponseDto(result));
    }

    @PreAuthorize("hasRole('MASTER')")
    @PostMapping("/bulk")
    public ResponseEntity<ApiResponse<List<ProductSummaryDto>>> getProductByIds(
            @RequestBody List<UUID> productIds
    ) {
        List<ProductSummaryDto> result = getProductsByIdsUseCase.getProductsByIds(productIds)
                .stream()
                .map(r -> new ProductSummaryDto(r.productId(), r.name(), r.price()))
                .toList();
        return ResponseUtil.success(result);
    }

    @GetMapping("/popular")
    public ResponseEntity<ApiResponse<List<PopularProductsResponseDto>>> getPopularProducts() {
        List<PopularProductsResponseDto> result = getPopularProductsUseCase.getPopularProducts()
                .stream()
                .map(r -> new PopularProductsResponseDto(
                        r.productId(), r.name(), r.price(), r.description(), r.category(), r.soldCount()))
                .toList();
        return ResponseUtil.success(result);
    }

    @PreAuthorize("hasAnyRole('MASTER', 'SHOW_HOST')")
    @PostMapping("/{productId}/live-discount")
    public ResponseEntity<ApiResponse<String>> applyLiveDiscount(
            @PathVariable UUID productId,
            @RequestBody @Valid LiveDiscountRequestDto request,
            @AuthenticationPrincipal RequestUserDetails userDetails
    ) {
        applyLiveDiscountUseCase.applyLiveDiscount(
                productId,
                request.discountPrice(),
                request.toDuration(),
                userDetails.getUserId()
        );
        return ResponseUtil.success("할인이 적용되었습니다.");
    }

    @GetMapping("/{productId}/price")
    public ResponseEntity<ApiResponse<ProductPriceResponseDto>> getProductPrice(@PathVariable UUID productId) {
        ProductPriceResult result = getProductPriceUseCase.getProductPrice(productId);
        return ResponseUtil.success(new ProductPriceResponseDto(
                result.productId(), result.currentPrice(), result.isDiscounted()));
    }

    @GetMapping("/{productId}/price-for-order")
    public ResponseEntity<ApiResponse<ProductOrderPriceDto>> getPriceForOrder(@PathVariable UUID productId) {
        ProductPriceResult result = getProductPriceUseCase.getPriceForOrder(productId);
        return ResponseUtil.success(new ProductOrderPriceDto(result.productId(), result.currentPrice()));
    }

    // --- mapping helpers ---

    private ProductCreateResponseDto toCreateResponseDto(ProductResult r) {
        return new ProductCreateResponseDto(
                r.productId(), r.name(), r.description(), r.price(), r.category(), r.productStatus(), r.companyId());
    }

    private ProductResponseDto toResponseDto(ProductResult r) {
        return new ProductResponseDto(
                r.productId(), r.name(), r.description(), r.price(), r.category(), r.productStatus(), r.companyId(), r.soldOut());
    }

    private ProductPageResponseDto toPageResponseDto(ProductPageResult r) {
        List<ProductResponseDto> dtos = r.content().stream().map(this::toResponseDto).toList();
        return new ProductPageResponseDto(
                dtos,
                new ProductPageResponseDto.PaginationMeta(r.page(), r.size(), r.totalPages(), r.totalElements(), false)
        );
    }
}
