package com.myworld.core.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * FIX (Medium): Replaced Guava in-memory LoadingCache with Redis.
 *
 * The previous Guava implementation reset on every server restart and was
 * useless behind a load balancer (each node had its own counter). Redis gives
 * a single, shared, TTL-backed store that survives restarts.
 *
 * Key scheme:
 *   login:user:<username>  → attempt count, TTL = blockMinutes
 *   login:ip:<ip>          → attempt count, TTL = blockMinutes
 */
@Service
public class LoginAttemptService {

    private final StringRedisTemplate redis;
    private final int maxAttempts;
    private final Duration blockDuration;

    public LoginAttemptService(
            StringRedisTemplate redis,
            @Value("${app.security.login.block-minutes:15}") int blockMinutes,
            @Value("${app.security.login.max-attempts:5}")   int maxAttempts) {

        this.redis         = redis;
        this.maxAttempts   = maxAttempts;
        this.blockDuration = Duration.ofMinutes(blockMinutes);
    }

    public void loginFailed(String username, String ip) {
        increment("login:user:" + username);
        increment("login:ip:"   + ip);
    }

    public void loginSuccess(String username, String ip) {
        redis.delete("login:user:" + username);
        redis.delete("login:ip:"   + ip);
    }

    public boolean isBlocked(String username, String ip) {
        return get("login:user:" + username) >= maxAttempts
            || get("login:ip:"   + ip)       >= maxAttempts;
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private void increment(String key) {
        Long count = redis.opsForValue().increment(key);
        if (count != null && count == 1) {
            // First attempt — set TTL so the block expires automatically
            redis.expire(key, blockDuration);
        }
    }

    private long get(String key) {
        String val = redis.opsForValue().get(key);
        if (val == null) return 0;
        try { return Long.parseLong(val); } catch (NumberFormatException e) { return 0; }
    }
}
