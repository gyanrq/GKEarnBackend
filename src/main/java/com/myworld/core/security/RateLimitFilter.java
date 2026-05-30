package com.myworld.core.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.distributed.proxy.ClientSideConfig;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.function.Supplier;

/**
 * FIX 2: Redis-backed distributed rate limiter using Bucket4j.
 *
 * WHY old Guava RateLimiter was wrong:
 *   - In-memory per-instance: behind a load balancer each instance allowed
 *     10 req/min, so 3 instances = 30 req/min effectively — limit is useless.
 *   - Resets on every deploy/restart — attacker just waits for a redeploy.
 *
 * WHY Bucket4j + Redis is correct:
 *   - All instances share one Redis key per (IP, endpoint-group).
 *   - Atomic CAS operations — no race conditions.
 *   - Survives restarts — bucket state is in Redis.
 *
 * FIX 8 (combined): Fail-open fallback — if Redis is unavailable at startup,
 * rate limiting is SKIPPED with a WARN log. The app stays running.
 *
 * Limits (per IP, per 60 seconds):
 *   /api/auth/mfa/**         → 5  requests
 *   /api/auth/**             → 10 requests
 *   /api/spin/**             → 5  requests
 *   /api/tasks/complete      → 12 requests
 *   /api/tasks/submit-lead   → 6  requests
 *   /api/payouts/request     → 3  requests
 *
 * Redis key format: rl:{group}:{ip}
 */
@Slf4j
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final BucketConfiguration AUTH_CONFIG = BucketConfiguration.builder()
            .addLimit(Bandwidth.builder().capacity(10).refillGreedy(10, Duration.ofMinutes(1)).build())
            .build();
    private static final BucketConfiguration MFA_CONFIG = BucketConfiguration.builder()
            .addLimit(Bandwidth.builder().capacity(5).refillGreedy(5, Duration.ofMinutes(1)).build())
            .build();
    private static final BucketConfiguration SPIN_CONFIG = BucketConfiguration.builder()
            .addLimit(Bandwidth.builder().capacity(5).refillGreedy(5, Duration.ofMinutes(1)).build())
            .build();
    private static final BucketConfiguration TASK_CONFIG = BucketConfiguration.builder()
            .addLimit(Bandwidth.builder().capacity(12).refillGreedy(12, Duration.ofMinutes(1)).build())
            .build();
    private static final BucketConfiguration TASK_LEAD_CONFIG = BucketConfiguration.builder()
            .addLimit(Bandwidth.builder().capacity(6).refillGreedy(6, Duration.ofMinutes(1)).build())
            .build();
    private static final BucketConfiguration PAYOUT_CONFIG = BucketConfiguration.builder()
            .addLimit(Bandwidth.builder().capacity(3).refillGreedy(3, Duration.ofMinutes(1)).build())
            .build();
    private static final BucketConfiguration WEBHOOK_CONFIG = BucketConfiguration.builder()
            .addLimit(Bandwidth.builder().capacity(60).refillGreedy(60, Duration.ofMinutes(1)).build())
            .build();

    private final ProxyManager<String> proxyManager;
    private final boolean redisAvailable;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public RateLimitFilter(
            @Value("${spring.data.redis.host:localhost}") String redisHost,
            @Value("${spring.data.redis.port:6379}") int redisPort,
            @Value("${spring.data.redis.password:}") String redisPassword) {

        ProxyManager<String> pm = null;
        boolean available = false;
        try {
            String uri = (redisPassword != null && !redisPassword.isBlank())
                    ? String.format("redis://:%s@%s:%d", redisPassword, redisHost, redisPort)
                    : String.format("redis://%s:%d", redisHost, redisPort);
            RedisClient client = RedisClient.create(uri);
            StatefulRedisConnection<String, byte[]> conn =
                    client.connect(RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE));
                    
            // FIXED: Using ClientSideConfig instead of deprecated withExpirationStrategy
            pm = LettuceBasedProxyManager.builderFor(conn)
                    .withClientSideConfig(ClientSideConfig.getDefault())
                    .build();
                    
            available = true;
            log.info("[RATE-LIMIT] Redis connected — distributed rate limiting active");
        } catch (Exception ex) {
            log.warn("[RATE-LIMIT] Redis unavailable — rate limiting DISABLED (fail-open). Cause: {}",
                    ex.getMessage());
        }
        this.proxyManager = pm;
        this.redisAvailable = available;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String path = request.getRequestURI();
        if (!isRateLimited(path)) { chain.doFilter(request, response); return; }
        if (!redisAvailable || proxyManager == null) {
            log.warn("[RATE-LIMIT] DEGRADED — skipping rate limit for path={}", path);
            chain.doFilter(request, response); return;
        }
        String ip = extractIp(request);
        String key = buildKey(path, ip);
        BucketConfiguration cfg = selectConfig(path);
        Supplier<BucketConfiguration> cfgSupplier = () -> cfg;
        var bucket = proxyManager.builder().build(key, cfgSupplier);
        if (!bucket.tryConsume(1)) {
            log.warn("[RATE-LIMIT] ip={} path={} → 429", ip, path);
            sendTooManyRequests(response); return;
        }
        chain.doFilter(request, response);
    }

    private boolean isRateLimited(String path) {
        return path.startsWith("/api/auth/") || path.startsWith("/api/v1/auth/")
            || path.startsWith("/api/spin/") || path.startsWith("/api/v1/spin/")
            || path.equals("/api/tasks/complete") || path.equals("/api/v1/tasks/complete")
            || path.equals("/api/tasks/submit-lead") || path.equals("/api/v1/tasks/submit-lead")
            || path.equals("/api/payouts/request") || path.equals("/api/v1/payouts/request");
    }
    private String buildKey(String path, String ip) {
        String g = path.contains("/mfa/") ? "mfa"
                : path.contains("/auth/") ? "auth"
                : path.contains("/spin/") ? "spin"
                : path.contains("/submit-lead") ? "task-lead"
                : path.contains("/tasks/complete") ? "task-complete"
                : path.contains("/webhook/razorpay") ? "webhook"
                : "payout-request";
        return "rl:" + g + ":" + ip;
    }
    private BucketConfiguration selectConfig(String path) {
        if (path.contains("/mfa/"))         return MFA_CONFIG;
        if (path.contains("/auth/"))        return AUTH_CONFIG;
        if (path.contains("/spin/"))        return SPIN_CONFIG;
        if (path.contains("/submit-lead"))  return TASK_LEAD_CONFIG;
        if (path.contains("/tasks/complete")) return TASK_CONFIG;
        if (path.contains("/webhook/razorpay")) return WEBHOOK_CONFIG;
        return PAYOUT_CONFIG;
    }
    @Value("${app.security.trusted-proxies:127.0.0.1}")
    private String trustedProxies;

    private String extractIp(HttpServletRequest req) {
        String remoteAddr = req.getRemoteAddr();
        java.util.List<String> trusted = java.util.Arrays.asList(trustedProxies.split(","));
        if (trusted.stream().anyMatch(t -> t.trim().equals(remoteAddr))) {
            String fwd = req.getHeader("X-Forwarded-For");
            if (fwd != null && !fwd.isBlank()) return fwd.split(",")[0].trim();
        }
        return remoteAddr;
    }
    private void sendTooManyRequests(HttpServletResponse res) throws IOException {
        res.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.setHeader("Retry-After", "60");
        objectMapper.writeValue(res.getWriter(), Map.of(
            "status", 429, "error", "Too Many Requests",
            "message", "Rate limit exceeded. Please wait before retrying."));
    }
}
