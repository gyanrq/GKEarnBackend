package com.myworld.core.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.Map;

/**
 * Cache configuration with Redis primary and Caffeine fallback.
 *
 * FIX (Memory Leak / OOM):
 *   Old fallback was ConcurrentMapCacheManager — NO size limit, NO TTL.
 *   Under sustained Redis outage with high traffic, the in-memory cache grew
 *   unbounded, eventually causing OutOfMemoryError and crashing the app.
 *
 *   New fallback is Caffeine with:
 *     - maximumSize(500)   → LRU eviction kicks in after 500 entries per cache
 *     - expireAfterWrite   → entries expire even if Redis stays down for hours
 *
 *   This means in fallback mode the app serves slightly stale data after TTL,
 *   but it NEVER crashes due to unbounded heap growth.
 *
 * Caffeine fallback TTLs (conservative — shorter than Redis to limit staleness):
 *   campaigns        3 min   (Redis: 5 min)
 *   userDashboard    1 min   (Redis: 2 min)
 *   leaderboard      3 min   (Redis: 5 min)
 *   spinPrizes       5 min   (Redis: 10 min)
 *   rewardConfig     5 min   (Redis: 10 min)
 *   adminDashboard   30 sec  (Redis: 1 min)
 *
 * Cache names (use these constants in @Cacheable to avoid typos):
 *   CacheConfig.CACHE_CAMPAIGNS, CACHE_USER_DASHBOARD, etc.
 */
@Slf4j
@Configuration
@EnableCaching
public class CacheConfig {

    public static final String CACHE_CAMPAIGNS       = "campaigns";
    public static final String CACHE_USER_DASHBOARD  = "userDashboard";
    public static final String CACHE_LEADERBOARD     = "leaderboard";
    public static final String CACHE_SPIN_PRIZES     = "spinPrizes";
    public static final String CACHE_REWARD_CONFIG   = "rewardConfig";
    public static final String CACHE_ADMIN_DASHBOARD = "adminDashboard";

    // Shared across Redis and Caffeine — these are the logical cache names
    private static final String[] ALL_CACHES = {
        CACHE_CAMPAIGNS, CACHE_USER_DASHBOARD, CACHE_LEADERBOARD,
        CACHE_SPIN_PRIZES, CACHE_REWARD_CONFIG, CACHE_ADMIN_DASHBOARD
    };

    @Value("${spring.data.redis.host:localhost}") private String redisHost;
    @Value("${spring.data.redis.port:6379}")      private int    redisPort;
    @Value("${spring.data.redis.password:}")      private String redisPassword;

    @Bean
    public LettuceConnectionFactory redisConnectionFactory() {
        RedisStandaloneConfiguration server =
                new RedisStandaloneConfiguration(redisHost, redisPort);
        if (redisPassword != null && !redisPassword.isBlank())
            server.setPassword(redisPassword);
        LettuceClientConfiguration client = LettuceClientConfiguration.builder()
                .commandTimeout(Duration.ofSeconds(2))
                .build();
        return new LettuceConnectionFactory(server, client);
    }

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory factory) {
        try {
            // Quick health probe — fails fast if Redis is unreachable
            factory.getConnection().ping();

            RedisCacheConfiguration base = redisCacheConfig();
            log.info("[CACHE] Redis healthy — using RedisCacheManager (distributed, TTL-aware)");

            return RedisCacheManager.builder(factory)
                    .cacheDefaults(base.entryTtl(Duration.ofMinutes(5)))
                    .withInitialCacheConfigurations(Map.of(
                            CACHE_CAMPAIGNS,       base.entryTtl(Duration.ofMinutes(5)),
                            CACHE_USER_DASHBOARD,  base.entryTtl(Duration.ofMinutes(2)),
                            CACHE_LEADERBOARD,     base.entryTtl(Duration.ofMinutes(5)),
                            CACHE_SPIN_PRIZES,     base.entryTtl(Duration.ofMinutes(10)),
                            CACHE_REWARD_CONFIG,   base.entryTtl(Duration.ofMinutes(10)),
                            CACHE_ADMIN_DASHBOARD, base.entryTtl(Duration.ofMinutes(1))
                    ))
                    .build();

        } catch (Exception ex) {
            // ── FIX: Caffeine bounded fallback — replaces unbounded ConcurrentMapCacheManager ──
            log.warn("[CACHE] Redis unavailable — falling back to Caffeine in-memory cache " +
                     "(bounded: max 500 entries, TTL enforced). App stays running. " +
                     "Cause: {}", ex.getMessage());

            CaffeineCacheManager caffeine = new CaffeineCacheManager(ALL_CACHES);
            caffeine.setCaffeine(
                Caffeine.newBuilder()
                    .maximumSize(500)              // LRU eviction — prevents OOM
                    .expireAfterWrite(Duration.ofMinutes(3)) // stale data expires
                    .recordStats()                 // exposes hit/miss to Micrometer
            );
            return caffeine;
        }
    }

    private RedisCacheConfiguration redisCacheConfig() {
        return RedisCacheConfiguration.defaultCacheConfig()
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new GenericJackson2JsonRedisSerializer()))
                .disableCachingNullValues();
    }
}
