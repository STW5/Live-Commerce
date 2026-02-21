package com.live_commerce.livebroadcast.domain.port.out;

import com.live_commerce.livebroadcast.application.dto.result.ExternalProductInfo;

import java.util.List;
import java.util.UUID;

public interface ExternalProductPort {
    ExternalProductInfo getProduct(UUID productId);
    List<ExternalProductInfo> getProducts(List<UUID> productIds);
}
