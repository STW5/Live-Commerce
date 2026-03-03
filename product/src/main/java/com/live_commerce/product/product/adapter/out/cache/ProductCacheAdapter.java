package com.live_commerce.product.product.adapter.out.cache;

import com.live_commerce.product.product.domain.port.out.ProductCachePort;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ProductCacheAdapter implements ProductCachePort {

    private final RedisTemplate<String, String> redisTemplate;

    private static final String DISCOUNT_KEY_PREFIX = "discount:";

    @Override
    public Optional<Integer> getDiscountPrice(UUID productId) {
        String key = DISCOUNT_KEY_PREFIX + productId;
        String value = redisTemplate.opsForValue().get(key);
        return Optional.ofNullable(value).map(Integer::valueOf);
    }

    @Override
    public void setDiscountPrice(UUID productId, int discountPrice, Duration duration) {
        String key = DISCOUNT_KEY_PREFIX + productId;
        redisTemplate.opsForValue().set(key, String.valueOf(discountPrice), duration);
    }
}
