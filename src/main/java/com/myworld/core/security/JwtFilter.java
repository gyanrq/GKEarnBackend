package com.myworld.core.security;

import com.myworld.core.constant.Role;
import com.myworld.modules.identity.domain.User;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * JWT authentication filter — validates token, checks blacklist, sets SecurityContext.
 *
 * Also injects MDC context for structured logging:
 *   - userId   → appears in every log line within this request
 *   - requestId → unique per request for correlation across logs
 *
 * FIX (JWT Blacklist): isTokenValid() now checks Redis — if the token's JTI
 * is blacklisted (user logged out / account blocked), the request is rejected
 * even if the signature and expiry are still valid.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final StringRedisTemplate redisTemplate;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        // ── Inject request correlation ID into MDC ────────────────────────────
        // FIX: Every log line within this request gets a unique requestId.
        // Pairs with distributed tracing (traceId from Micrometer) for full
        // observability without needing to grep raw text.
        String requestId = request.getHeader("X-Request-ID");
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        }
        MDC.put("requestId", requestId);
        response.setHeader("X-Request-ID", requestId); // echo back to client

        try {
            String authHeader = request.getHeader("Authorization");
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                filterChain.doFilter(request, response);
                return;
            }

            String token = authHeader.substring(7);

            // FIX: isTokenValid now also checks Redis blacklist
            if (jwtUtil.isTokenValid(token) &&
                    SecurityContextHolder.getContext().getAuthentication() == null) {

                Claims claims    = jwtUtil.parseClaims(token);
                String  email    = claims.getSubject();
                Long    userId   = claims.get("userId", Long.class);
                
                if (userId != null && Boolean.TRUE.equals(redisTemplate.hasKey("user:blocked:" + userId))) {
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Account suspended");
                    return;
                }

                List<String> roles = claims.get("roles", List.class);

                // Inject userId into MDC so every log in this request includes it
                if (userId != null) MDC.put("userId", String.valueOf(userId));

                List<SimpleGrantedAuthority> authorities = roles.stream()
                        .map(SimpleGrantedAuthority::new)
                        .collect(Collectors.toList());

                User minimalUser = new User();
                minimalUser.setId(userId);
                minimalUser.setEmail(email);
                if (roles != null && !roles.isEmpty()) {
                    String roleStr = roles.get(0).replace("ROLE_", "");
                    try { minimalUser.setRole(Role.valueOf(roleStr)); }
                    catch (IllegalArgumentException ignored) {}
                }

                CustomUserDetails userDetails = new CustomUserDetails(minimalUser);
                UsernamePasswordAuthenticationToken auth =
                        new UsernamePasswordAuthenticationToken(userDetails, null, authorities);
                auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(auth);
            }

            filterChain.doFilter(request, response);

        } finally {
            // Always clear MDC after request — prevents leakage to next request on same thread
            MDC.remove("requestId");
            MDC.remove("userId");
        }
    }
}
