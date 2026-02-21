package com.live_commerce.livebroadcast.application.dto.result;

import java.util.UUID;

public record ExternalProductInfo(
        UUID productId,
        UUID companyId
) {}
