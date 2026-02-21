package com.live_commerce.livebroadcast.adapter.out.client;

import com.live_commerce.livebroadcast.application.dto.result.ExternalProductInfo;
import com.live_commerce.livebroadcast.domain.port.out.ExternalProductPort;
import com.live_commerce.livebroadcast.infrastructure.client.product.ExternalProductResponseDto;
import com.live_commerce.livebroadcast.infrastructure.client.product.ProductClient;
import com.live_commerce.livebroadcast.infrastructure.client.product.ProductSummaryDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ProductFeignAdapter implements ExternalProductPort {

    private final ProductClient productClient;

    @Override
    public ExternalProductInfo getProduct(UUID productId) {
        ExternalProductResponseDto dto = productClient.getProduct(productId).getData();
        return new ExternalProductInfo(dto.productId(), dto.companyId());
    }

    @Override
    public List<ExternalProductInfo> getProducts(List<UUID> productIds) {
        List<ProductSummaryDto> dtos = productClient.getProducts(productIds).getData();
        return dtos.stream()
                .map(dto -> new ExternalProductInfo(dto.productId(), null))
                .toList();
    }
}
