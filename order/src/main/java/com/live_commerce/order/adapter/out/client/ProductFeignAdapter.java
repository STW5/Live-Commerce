package com.live_commerce.order.adapter.out.client;

import com.live_commerce.order.application.dto.result.ProductInfo;
import com.live_commerce.order.domain.model.vo.Money;
import com.live_commerce.order.domain.port.out.ProductQueryPort;
import com.live_commerce.order.infrastructure.client.feign.ProductClient;
import com.live_commerce.order.infrastructure.client.response.InventoryCheckResponseDto;
import com.live_commerce.order.infrastructure.client.response.ProductCreateResponseDto;
import com.live_commerce.order.presentation.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * ProductQueryPort 구현체
 * - Feign 응답 DTO(ProductCreateResponseDto) → Application DTO(ProductInfo) + VO(Money) 변환
 * - Application/Domain Layer가 Feign에 의존하지 않도록 격리
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProductFeignAdapter implements ProductQueryPort {

    private final ProductClient productClient;

    @Override
    public ProductInfo findById(UUID productId) {
        ApiResponse<ProductCreateResponseDto> response = productClient.getProduct(productId);
        ProductCreateResponseDto dto = response.getData();
        if (dto == null) {
            return null;
        }
        // Feign DTO → Application DTO + Money VO 변환 (어댑터의 핵심 역할)
        return new ProductInfo(dto.productId(), dto.name(), Money.of(dto.price()));
    }

    @Override
    public boolean isOrderable(UUID productId, int quantity) {
        ApiResponse<InventoryCheckResponseDto> response =
                productClient.checkOrderableInventory(productId, quantity);
        InventoryCheckResponseDto dto = response.getData();
        return dto != null && dto.orderAvailable();
    }
}
