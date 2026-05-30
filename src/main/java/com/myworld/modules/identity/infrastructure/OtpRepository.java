package com.myworld.modules.identity.infrastructure;

import com.myworld.modules.identity.domain.Otp;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.OffsetDateTime;
import java.util.Optional;

public interface OtpRepository extends JpaRepository<Otp, Long> {
    Optional<Otp> findTopByIdentifierOrderByIdDesc(String identifier);

    @Query("SELECT COUNT(o) FROM Otp o WHERE o.identifier = :id AND o.createdAt >= :since")
    long countRecentOtps(@Param("id") String identifier, @Param("since") OffsetDateTime since);
}
