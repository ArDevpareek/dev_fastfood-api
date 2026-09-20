package com.dev.fastfood.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import tools.jackson.databind.jsontype.PolymorphicTypeValidator;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

// CachingConfigurer lets us plug a custom CacheErrorHandler into Spring's
// @Cacheable/@CacheEvict machinery (see errorHandler() below) — it's the
// standard extension point for this, not something we're inventing.
@Configuration
public class CacheConfig implements CachingConfigurer {

    private static final Logger log = LoggerFactory.getLogger(CacheConfig.class);

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {

        // Without default typing, the serializer writes plain JSON with no
        // record of which Java class each object was — so on a cache HIT,
        // Jackson has nothing to deserialize back into MenuItem/Restaurant
        // and hands back raw LinkedHashMaps instead, which blew up with a
        // ClassCastException the moment the controller tried to use them.
        // enableDefaultTyping() embeds that type info. We scope it to our
        // own package (+ java.util, for List/ArrayList) via the validator
        // below rather than allowing any class — this cache is only ever
        // written to by our own app, but there's no reason to deserialize
        // arbitrary types from it either.
        // Our entities are built from plain JDK value types (BigDecimal,
        // OffsetDateTime, UUID, collections, ...) and those get type-tagged
        // too, not just our own classes — so each of those packages needs
        // to be allowed as well, or deserialization rejects them the same
        // way it would reject an unrecognized one.
        PolymorphicTypeValidator typeValidator = BasicPolymorphicTypeValidator.builder()
                .allowIfSubType("com.dev.fastfood.")
                .allowIfSubType("java.util.")
                .allowIfSubType("java.math.")
                .allowIfSubType("java.time.")
                .allowIfSubType("java.lang.")
                .build();

        GenericJacksonJsonRedisSerializer jsonSerializer =
                GenericJacksonJsonRedisSerializer.builder()
                        .enableDefaultTyping(typeValidator)
                        .build();

        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(5))
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(jsonSerializer));

        Map<String, RedisCacheConfiguration> cacheConfigs = new HashMap<>();
        // Restaurants rarely change (no create/update endpoint calls this
        // yet), so a longer TTL is safe — fewer DB hits, and staleness
        // is capped at 10 minutes worst case.
        cacheConfigs.put("restaurants", defaultConfig.entryTtl(Duration.ofMinutes(10)));
        // Menus change more often (new items, availability toggles), so
        // we keep this one short — 2 minutes caps how stale a menu can get.
        cacheConfigs.put("menus", defaultConfig.entryTtl(Duration.ofMinutes(2)));

        RedisCacheWriter cacheWriter = RedisCacheWriter.nonLockingRedisCacheWriter(connectionFactory);

        // Using our own manager (instead of RedisCacheManager.builder())
        // so every cache it creates is hit/miss-observable — see
        // ObservableRedisCacheManager / ObservableRedisCache.
        return new ObservableRedisCacheManager(cacheWriter, defaultConfig, cacheConfigs);
    }

    // Without this, a Redis outage turns GET /restaurants and GET /menu
    // into 500s — even though both could still be answered from Postgres.
    // Spring's cache interceptor calls this handler instead of letting a
    // Redis exception propagate; as long as we don't rethrow, it treats
    // the failed lookup as a miss and runs the real (DB) method normally.
    @Bean
    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {

            @Override
            public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
                log.warn("Redis GET failed for cache '{}' key '{}' — falling back to the database. Cause: {}",
                        cache.getName(), key, exception.toString());
            }

            @Override
            public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
                log.warn("Redis PUT failed for cache '{}' key '{}' — response was served but not cached. Cause: {}",
                        cache.getName(), key, exception.toString());
            }

            @Override
            public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
                log.warn("Redis EVICT failed for cache '{}' key '{}' — stale data may be served until its TTL expires. Cause: {}",
                        cache.getName(), key, exception.toString());
            }

            @Override
            public void handleCacheClearError(RuntimeException exception, Cache cache) {
                log.warn("Redis CLEAR failed for cache '{}'. Cause: {}", cache.getName(), exception.toString());
            }
        };
    }
}
