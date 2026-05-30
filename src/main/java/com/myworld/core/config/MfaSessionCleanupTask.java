package com.myworld.core.config;

import com.myworld.modules.identity.infrastructure.MfaPendingSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

/**
 * Periodically removes expired MFA pending sessions.
 * These are created at login when MFA is required and are short-lived (10 min default).
 * Without cleanup they accumulate in the DB over time.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MfaSessionCleanupTask {

    private final MfaPendingSessionRepository sessionRepo;

    /** Run every 30 minutes */
    @Scheduled(fixedDelay = 30 * 60 * 1000)
    @Transactional
    public void cleanupExpiredSessions() {
        sessionRepo.deleteExpired(OffsetDateTime.now());
        log.debug("MFA pending session cleanup complete");
    }
}