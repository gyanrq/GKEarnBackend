package com.myworld.core.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * FIX 4: API versioning — rewrites /api/v1/* → /api/* transparently.
 *
 * Why this matters for React Native:
 *   Once your app is on the Play Store, users don't update immediately.
 *   Without versioning, any breaking backend change silently breaks old app
 *   versions. With /api/v1/*, you can introduce /api/v2/* for breaking changes
 *   while v1 clients continue working.
 *
 * Old clients:       /api/campaigns      → works unchanged
 * New RN/Web code:   /api/v1/campaigns   → rewritten to /api/campaigns
 * Future v2:         /api/v2/campaigns   → add separate handling here
 */
@Slf4j
@Component
@Order(1)
public class ApiVersionFilter implements Filter {

    private static final String V1_PREFIX  = "/api/v1";
    private static final String API_PREFIX = "/api";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpReq = (HttpServletRequest) request;
        String uri = httpReq.getRequestURI();
        if (uri.startsWith(V1_PREFIX + "/") || uri.equals(V1_PREFIX)) {
            String rewritten = API_PREFIX + uri.substring(V1_PREFIX.length());
            log.debug("[API-VERSION] {} {} → {}", httpReq.getMethod(), uri, rewritten);
            chain.doFilter(new RewrittenRequest(httpReq, rewritten), response);
        } else {
            chain.doFilter(request, response);
        }
    }

    private static class RewrittenRequest extends HttpServletRequestWrapper {
        private final String uri;
        RewrittenRequest(HttpServletRequest req, String uri) { super(req); this.uri = uri; }
        @Override public String getRequestURI()  { return uri; }
        @Override public String getServletPath() { return uri; }
        @Override public String getPathInfo()    { return null; }
    }
}
