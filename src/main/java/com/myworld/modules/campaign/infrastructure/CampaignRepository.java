package com.myworld.modules.campaign.infrastructure;

import com.myworld.modules.campaign.domain.Campaign;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;

public interface CampaignRepository extends JpaRepository<Campaign, Long> {

    // FIX: replaced findByIsActiveTrue with a query that also excludes campaigns
    // whose endAt has already passed. Previously a campaign whose end date had
    // elapsed would still appear in the user-facing list if the admin hadn't
    // manually deactivated it.
    @Query("""
        SELECT c FROM Campaign c
        WHERE c.isActive = true
          AND (c.endAt IS NULL OR c.endAt > :now)
    """)
    Page<Campaign> findActiveAndNotExpired(@Param("now") OffsetDateTime now, Pageable pageable);

    // Keep the plain isActive query for admin use (admin sees all active campaigns
    // regardless of endAt so they can manage them).
    Page<Campaign> findByIsActiveTrue(Pageable pageable);

    long countByIsActive(Boolean isActive);
}