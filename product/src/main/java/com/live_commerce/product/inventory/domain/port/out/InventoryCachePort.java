package com.live_commerce.product.inventory.domain.port.out;

import java.util.AbstractMap;
import java.util.List;
import java.util.UUID;

public interface InventoryCachePort {
    void incrementSoldCount(UUID productId, int quantity);
    List<AbstractMap.SimpleEntry<UUID, Long>> getTopSoldCounts(int limit);
}
