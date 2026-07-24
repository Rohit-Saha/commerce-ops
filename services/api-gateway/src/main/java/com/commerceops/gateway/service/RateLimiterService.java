package com.commerceops.gateway.service;

import com.commerceops.gateway.config.GatewayProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * Redis-backed sliding-window rate limiter. Each identifier (API key or client IP) gets a
 * sorted set of request timestamps; a Lua script atomically trims expired entries, counts
 * the remainder and either admits or rejects the current request.
 */
@Service
public class RateLimiterService {

    private static final Logger log = LoggerFactory.getLogger(RateLimiterService.class);
    private static final String KEY_PREFIX = "gateway:ratelimit:";

    private static final String SCRIPT = """
            local key = KEYS[1]
            local now = tonumber(ARGV[1])
            local window = tonumber(ARGV[2])
            local limit = tonumber(ARGV[3])

            redis.call('ZREMRANGEBYSCORE', key, '-inf', now - window)
            local count = redis.call('ZCARD', key)

            if count < limit then
              redis.call('ZADD', key, now, tostring(now) .. '-' .. tostring(math.random(1, 1000000000)))
              redis.call('PEXPIRE', key, window)
              return {1, limit - count - 1}
            end

            local oldest = redis.call('ZRANGE', key, 0, 0, 'WITHSCORES')
            local retryAfterMs = window
            if oldest[2] ~= nil then
              retryAfterMs = (tonumber(oldest[2]) + window) - now
              if retryAfterMs < 0 then
                retryAfterMs = 0
              end
            end
            return {0, retryAfterMs}
            """;

    private final StringRedisTemplate redisTemplate;
    private final GatewayProperties properties;
    private final RedisScript<List> script = new DefaultRedisScript<>(SCRIPT, List.class);

    public RateLimiterService(StringRedisTemplate redisTemplate, GatewayProperties properties) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    public RateLimitResult tryAcquire(String identifier) {
        int limit = properties.rateLimit().limit();
        long windowMs = properties.rateLimit().windowSeconds() * 1000L;
        long now = Instant.now().toEpochMilli();
        String key = KEY_PREFIX + identifier;

        try {
            @SuppressWarnings("unchecked")
            List<Long> result = redisTemplate.execute(
                    script,
                    List.of(key),
                    String.valueOf(now),
                    String.valueOf(windowMs),
                    String.valueOf(limit));

            boolean allowed = result != null && result.get(0) == 1L;
            long second = result != null ? result.get(1) : 0L;
            if (allowed) {
                return RateLimitResult.allowed(limit, second);
            }
            long retryAfterSeconds = Math.max(1L, (second + 999) / 1000);
            return RateLimitResult.rejected(limit, retryAfterSeconds);
        } catch (Exception ex) {
            log.warn("Rate limiter unavailable, allowing request for {}: {}", identifier, ex.getMessage());
            return RateLimitResult.allowed(limit, limit);
        }
    }
}
