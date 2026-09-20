package com.dev.fastfood.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.cache.RedisCache;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheWriter;

// A RedisCache that logs whether each lookup was a hit or a miss.
// Plain @Cacheable gives no visibility into this — the method body only
// ever runs on a miss, so there's nowhere in our own code to log a hit.
// Overriding lookup() (the one extension point RedisCache exposes for
// this) lets us log both cases without changing any caching behavior.
public class ObservableRedisCache extends RedisCache {

    private static final Logger log = LoggerFactory.getLogger(ObservableRedisCache.class);

    protected ObservableRedisCache(String name, RedisCacheWriter cacheWriter, RedisCacheConfiguration cacheConfig) {
        super(name, cacheWriter, cacheConfig);
    }

    @Override
    protected Object lookup(Object key) {
        Object value = super.lookup(key);
        if (value != null) {
            log.info("Cache HIT  [{}] key={}", getName(), key);
        } else {
            log.info("Cache MISS [{}] key={} — loading from the database", getName(), key);
        }
        return value;
    }
}
