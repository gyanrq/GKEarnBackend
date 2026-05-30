package com.myworld.core.security;

import org.junit.jupiter.api.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the Redis-backed LoginAttemptService.
 * Uses a simple in-memory map to simulate Redis StringRedisTemplate,
 * so no live Redis connection is required.
 */
@DisplayName("LoginAttemptService — Unit Tests (Redis)")
class LoginAttemptServiceTest {

    private LoginAttemptService service;
    private Map<String, String>  store;

    @BeforeEach
    void setUp() {
        store = new HashMap<>();
        StringRedisTemplate redis = buildFakeRedis(store);
        service = new LoginAttemptService(redis, 15, 5);
    }

    @Test @DisplayName("new user is not blocked")
    void newUser_notBlocked() {
        assertThat(service.isBlocked("user@x.com", "1.2.3.4")).isFalse();
    }

    @Test @DisplayName("blocked after 5 failed attempts by username")
    void blockedAfterMaxAttempts_byUsername() {
        for (int i = 0; i < 5; i++) service.loginFailed("user@x.com", "1.2.3.4");
        assertThat(service.isBlocked("user@x.com", "9.9.9.9")).isTrue();
    }

    @Test @DisplayName("blocked after 5 failed attempts by IP")
    void blockedAfterMaxAttempts_byIp() {
        for (int i = 0; i < 5; i++) service.loginFailed("other@x.com", "5.5.5.5");
        assertThat(service.isBlocked("totally-new@x.com", "5.5.5.5")).isTrue();
    }

    @Test @DisplayName("not blocked after only 4 failed attempts")
    void notBlockedAt4Attempts() {
        for (int i = 0; i < 4; i++) service.loginFailed("user@x.com", "1.2.3.4");
        assertThat(service.isBlocked("user@x.com", "1.2.3.4")).isFalse();
    }

    @Test @DisplayName("successful login clears attempt counter")
    void loginSuccess_clearsCounter() {
        for (int i = 0; i < 5; i++) service.loginFailed("user@x.com", "1.2.3.4");
        assertThat(service.isBlocked("user@x.com", "1.2.3.4")).isTrue();
        service.loginSuccess("user@x.com", "1.2.3.4");
        assertThat(service.isBlocked("user@x.com", "1.2.3.4")).isFalse();
    }

    @Test @DisplayName("different users have independent attempt counters")
    void differentUsersIndependent() {
        for (int i = 0; i < 5; i++) service.loginFailed("userA@x.com", "1.1.1.1");
        assertThat(service.isBlocked("userB@x.com", "2.2.2.2")).isFalse();
    }

    @Test @DisplayName("successive failed attempts beyond max do not cause exception")
    void beyondMaxAttempts_noException() {
        for (int i = 0; i < 10; i++) service.loginFailed("user@x.com", "1.2.3.4");
        assertThatCode(() -> service.isBlocked("user@x.com", "1.2.3.4")).doesNotThrowAnyException();
        assertThat(service.isBlocked("user@x.com", "1.2.3.4")).isTrue();
    }

    // ── test helper: fake Redis using a plain HashMap ─────────────────────────

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static StringRedisTemplate buildFakeRedis(Map<String, String> store) {
        ValueOperations<String, String> ops = mock(ValueOperations.class);

        // increment: increment in map, return new value
        doAnswer(inv -> {
            String key = inv.getArgument(0);
            long newVal = Long.parseLong(store.getOrDefault(key, "0")) + 1;
            store.put(key, String.valueOf(newVal));
            return newVal;
        }).when(ops).increment(anyString());

        // get
        doAnswer(inv -> store.get(inv.<String>getArgument(0)))
            .when(ops).get(anyString());

        StringRedisTemplate t = mock(StringRedisTemplate.class);
        when(t.opsForValue()).thenReturn((ValueOperations) ops);

        // delete: remove from map
        doAnswer(inv -> { store.remove(inv.<String>getArgument(0)); return null; })
            .when(t).delete(anyString());

        // expire: no-op for tests (TTL not needed)
        doReturn(true).when(t).expire(anyString(), any(Duration.class));

        return t;
    }
}
