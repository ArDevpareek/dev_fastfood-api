package com.dev.fastfood.config;

import org.springframework.data.redis.cache.RedisCache;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheWriter;

import java.util.Map;

// Same as RedisCacheManager, except every cache it hands out is an
// ObservableRedisCache instead of a plain RedisCache, so hit/miss
// logging applies to every @Cacheable cache in the app automatically.
public class ObservableRedisCacheManager extends RedisCacheManager {

    public ObservableRedisCacheManager(RedisCacheWriter cacheWriter,
                                       RedisCacheConfiguration defaultCacheConfiguration,
                                       Map<String, RedisCacheConfiguration> initialCacheConfigurations) {
        super(cacheWriter, defaultCacheConfiguration, initialCacheConfigurations);
    }

    @Override
    protected RedisCache createRedisCache(String name, RedisCacheConfiguration cacheConfig) {
        return new ObservableRedisCache(
                name,
                getCacheWriter(),
                cacheConfig != null ? cacheConfig : getDefaultCacheConfiguration());
    }
}
