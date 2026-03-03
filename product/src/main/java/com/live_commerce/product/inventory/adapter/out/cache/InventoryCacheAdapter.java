package com.live_commerce.product.inventory.adapter.out.cache;

import com.live_commerce.product.inventory.domain.port.out.InventoryCachePort;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
@RequiredArgsConstructor
public class InventoryCacheAdapter implements InventoryCachePort {

    private final StringRedisTemplate redisTemplate;

    private static final String SOLD_COUNT_KEY_PREFIX = "product:sold_count:";

    @Override
    public void incrementSoldCount(UUID productId, int quantity) {
        String key = SOLD_COUNT_KEY_PREFIX + productId;
        redisTemplate.opsForValue().increment(key, quantity);
    }

    @Override
    public List<AbstractMap.SimpleEntry<UUID, Long>> getTopSoldCounts(int limit) {
        Set<String> keys = redisTemplate.keys(SOLD_COUNT_KEY_PREFIX + "*");
        if (keys == null || keys.isEmpty()) {
            return Collections.emptyList();
        }

        List<AbstractMap.SimpleEntry<UUID, Long>> soldCounts = new ArrayList<>();

        List<String> values = redisTemplate.opsForValue().multiGet(keys);
        int idx = 0;
        for (String key : keys) {
            String value = (values != null) ? values.get(idx) : null;
            if (value != null) {
                UUID productId = UUID.fromString(key.replace(SOLD_COUNT_KEY_PREFIX, ""));
                long count = Long.parseLong(value);
                soldCounts.add(new AbstractMap.SimpleEntry<>(productId, count));
            }
            idx++;
        }

        return soldCounts.stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .limit(limit)
                .toList();
    }
}
