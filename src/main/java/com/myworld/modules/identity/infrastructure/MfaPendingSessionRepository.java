package com.myworld.modules.identity.infrastructure;

import com.myworld.modules.identity.domain.MfaPendingSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Optional;

public interface MfaPendingSessionRepository extends JpaRepository<MfaPendingSession, Long> {

    Optional<MfaPendingSession> findBySessionToken(String sessionToken);

    @Modifying
    @Query("DELETE FROM MfaPendingSession s WHERE s.expiresAt < :now")
    void deleteExpired(@Param("now") OffsetDateTime now);

    @Modifying
    @Query("DELETE FROM MfaPendingSession s WHERE s.user.id = :userId")
    void deleteByUserId(@Param("userId") Long userId);
}