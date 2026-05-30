package com.myworld.core.config;

import com.myworld.core.security.JwtFilter;
import com.myworld.core.security.RateLimitFilter;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.*;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtFilter jwtFilter;
    private final RateLimitFilter rateLimitFilter;

    @Value("${app.cors.allowed-origins:http://localhost:3000}")
    private String allowedOrigins;

    @Value("${spring.profiles.active:dev}")
    private String activeProfile;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(csrf -> csrf.disable())
            .headers(headers -> headers
                .contentSecurityPolicy(csp -> csp.policyDirectives(
                    "default-src 'self'; " +
                    "script-src 'self'; " +
                    "style-src 'self' 'unsafe-inline'; " +
                    "img-src 'self' data: https:; " +
                    "font-src 'self' data:; " +
                    "connect-src 'self'; " +
                    "frame-ancestors 'none'; " +
                    "base-uri 'self'; " +
                    "form-action 'self'"))
                .permissionsPolicyHeader(policy -> policy.policy(
                    "camera=(), microphone=(), geolocation=(), payment=(), usb=(), fullscreen=(self)"))
                .referrerPolicy(referrer -> referrer
                    .policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN)))
            .exceptionHandling(exc -> exc.authenticationEntryPoint((request, response, authException) ->
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized")))
            .authorizeHttpRequests(auth -> {
                // Public auth endpoints — no token required
                auth.requestMatchers(
                    // Unversioned paths — existing clients
                    "/api/auth/login",
                    "/api/auth/register",
                    "/api/auth/refresh-token",
                    "/api/auth/forgot-password",
                    "/api/auth/reset-password",
                    "/api/auth/mfa/verify-email-otp",
                    "/api/auth/google",
                    "/api/auth/mfa/verify-mobile-otp",
                    "/api/auth/mfa/verify-totp",
                    "/api/auth/mfa/complete",
                    // FIX: Versioned /api/v1/* paths — rewritten by ApiVersionFilter before
                    // Security runs, but permitAll must also cover the original versioned URI
                    // because the filter runs in the same request cycle.
                    "/api/v1/auth/login",
                    "/api/v1/auth/register",
                    "/api/v1/auth/refresh-token",
                    "/api/v1/auth/forgot-password",
                    "/api/v1/auth/reset-password",
                    "/api/v1/auth/mfa/verify-email-otp",
                    "/api/v1/auth/google",
                    "/api/v1/auth/mfa/verify-mobile-otp",
                    "/api/v1/auth/mfa/verify-totp",
                    "/api/v1/auth/mfa/complete"
                ).permitAll();
                // Actuator: only /health is public — all other actuator endpoints require ADMIN.
                // This prevents future endpoints (env, beans, mappings …) from leaking publicly
                // if someone adds them without thinking.
                auth.requestMatchers("/actuator/health").permitAll()
                    .requestMatchers("/actuator/**").hasRole("ADMIN");
                    
                // Swagger — restricted in non-dev profiles
                if ("dev".equals(activeProfile)) {
                    auth.requestMatchers("/v3/api-docs/**", "/swagger-ui/**").permitAll();
                } else {
                    auth.requestMatchers("/v3/api-docs/**", "/swagger-ui/**").hasRole("ADMIN");
                }
                
                // Public APIs
                auth.requestMatchers("/api/public/**").permitAll()
                    .requestMatchers("/api/files/public/**").permitAll()
                // Role-restricted admin/mod
                    .requestMatchers("/api/admin/**", "/api/v1/admin/**").hasRole("ADMIN")
                    .requestMatchers("/api/mod/**", "/api/v1/mod/**").hasAnyRole("MODERATOR", "ADMIN")
                // Everything else requires a valid JWT
                    .anyRequest().authenticated();
            })
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
            // Rate limiter runs before JWT validation so unauthenticated brute-force
            // attempts are stopped at the filter boundary.
            .addFilterBefore(rateLimitFilter, JwtFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOrigins(Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .map(s -> s.replaceAll("/+$", ""))
                .filter(s -> !s.isEmpty())
                .toList());
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cfg.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "Origin"));
        cfg.setExposedHeaders(List.of("Authorization"));
        cfg.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cfg);
        return source;
    }
}
