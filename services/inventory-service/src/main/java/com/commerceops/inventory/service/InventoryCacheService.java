package com.commerceops.inventory.service;

import com.commerceops.inventory.domain.StockItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Write-through cache in front of the {@code stock_items} table, keyed by
 * {@code inventory:stock:{sku}} and holding the current available quantity.
 */
@Service
public class InventoryCacheService {

    private static final Logger log = LoggerFactory.getLogger(InventoryCacheService.class);
    private static final String KEY_PREFIX = "inventory:stock:";

    private final StringRedisTemplate redisTemplate;

    public InventoryCacheService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void writeThrough(StockItem item) {
        try {
            redisTemplate.opsForValue().set(key(item.getSku()), String.valueOf(item.getAvailableQty()));
        } catch (Exception ex) {
            log.warn("Failed to write-through cache for sku={}: {}", item.getSku(), ex.getMessage());
        }
    }

    public Optional<Integer> getCachedAvailableQty(String sku) {
        try {
            String value = redisTemplate.opsForValue().get(key(sku));
            return Optional.ofNullable(value).map(Integer::valueOf);
        } catch (Exception ex) {
            log.warn("Failed to read cache for sku={}: {}", sku, ex.getMessage());
            return Optional.empty();
        }
    }

    public void evict(String sku) {
        try {
            redisTemplate.delete(key(sku));
        } catch (Exception ex) {
            log.warn("Failed to evict cache for sku={}: {}", sku, ex.getMessage());
        }
    }

    private String key(String sku) {
        return KEY_PREFIX + sku;
    }
}
