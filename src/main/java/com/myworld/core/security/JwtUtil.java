package com.myworld.core.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * JWT utility: generate, parse, validate, and blacklist tokens.
 *
 * FIX — JWT Blacklisting:
 *   Previously, logging out only deleted the token client-side. If a token was
 *   stolen or an account was compromised, the attacker had full access until
 *   natural expiry (up to 24 hours).
 *
 *   Now: every token has a unique JTI (JWT ID) claim. On logout, the JTI is
 *   stored in Redis with a TTL matching the token's remaining lifespan.
 *   JwtFilter checks this blacklist on every request — blacklisted tokens are
 *   rejected immediately even if the signature is valid.
 *
 * Redis key format:  jwt:blacklist:{jti}
 * Redis TTL:         remaining lifespan of the token (so keys auto-expire)
 *
 * Usage:
 *   jwtUtil.blacklistToken(token)   — call on logout or account block
 *   jwtUtil.isTokenValid(token)     — returns false if blacklisted
 */
@Slf4j
@Component
public class JwtUtil {

    private static final String BLACKLIST_PREFIX = "jwt:blacklist:";

    private final SecretKey            key;
    private final StringRedisTemplate  redis;

    @Value("${app.jwt.expiration-ms:86400000}")
    private long jwtExpirationMs;

    public JwtUtil(
            @Value("${app.jwt.secret}") String secret,
            StringRedisTemplate redis) {
        this.key   = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.redis = redis;
    }

    // ── Token generation ──────────────────────────────────────────────────────

    public String generateAccessToken(String email, Long userId,
                                      Collection<? extends GrantedAuthority> authorities) {
        List<String> roles = authorities.stream()
                .map(GrantedAuthority::getAuthority).toList();

        return Jwts.builder()
                .id(UUID.randomUUID().toString())   // FIX: unique JTI for blacklisting
                .subject(email)
                .claim("userId", userId)
                .claim("roles", roles)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + jwtExpirationMs))
                .signWith(key)
                .compact();
    }

    // ── Parsing ───────────────────────────────────────────────────────────────

    public Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    // ── Validation (signature + expiry + blacklist) ───────────────────────────

    public boolean isTokenValid(String token) {
        try {
            Claims claims = parseClaims(token);

            // FIX: Check Redis blacklist — reject if JTI is blacklisted
            String jti = claims.getId();
            if (jti != null && isBlacklisted(jti)) {
                log.warn("[JWT] Token rejected — JTI is blacklisted: {}", jti);
                return false;
            }
            return true;

        } catch (JwtException | IllegalArgumentException e) {
            log.warn("[JWT] Invalid token: {}", e.getMessage());
            return false;
        }
    }

    // ── Blacklisting ──────────────────────────────────────────────────────────

    /**
     * Blacklists a token so it is rejected on all future requests.
     * Called on: logout, account block, password change.
     *
     * Stores the JTI in Redis with TTL = token's remaining lifespan.
     * The Redis key auto-expires — no cleanup job needed.
     *
     * Fails gracefully: if Redis is down, logs a warning but does NOT crash.
     * In Redis-down scenarios the token remains valid until natural expiry —
     * this is a conscious fail-open decision to keep the app running.
     */
    public void blacklistToken(String token) {
        try {
            Claims claims = parseClaims(token);
            String jti = claims.getId();
            if (jti == null || jti.isBlank()) {
                log.warn("[JWT] Cannot blacklist token — no JTI claim present");
                return;
            }
            long remainingMs = claims.getExpiration().getTime() - System.currentTimeMillis();
            if (remainingMs <= 0) {
                log.debug("[JWT] Token already expired — blacklist skipped");
                return;
            }
            redis.opsForValue().set(
                BLACKLIST_PREFIX + jti,
                "revoked",
                Duration.ofMillis(remainingMs)
            );
            log.info("[JWT] Token blacklisted: jti={} ttl={}ms", jti, remainingMs);
        } catch (Exception ex) {
            log.warn("[JWT] Failed to blacklist token (Redis down?): {}", ex.getMessage());
        }
    }

    /** Extracts JTI without full validation — used for blacklisting expired tokens. */
    public String extractJti(String token) {
        try { return parseClaims(token).getId(); }
        catch (Exception e) { return null; }
    }

    private boolean isBlacklisted(String jti) {
        try {
            return Boolean.TRUE.equals(redis.hasKey(BLACKLIST_PREFIX + jti));
        } catch (Exception ex) {
            // Redis down — fail open (don't reject valid tokens)
            log.warn("[JWT] Redis unavailable for blacklist check — failing open: {}", ex.getMessage());
            return false;
        }
    }
}
