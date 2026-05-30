package com.myworld.modules.campaign.infrastructure;

import com.myworld.modules.campaign.domain.Lead;
import com.myworld.modules.campaign.domain.LeadStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LeadRepository extends JpaRepository<Lead, Long> {
    Page<Lead> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
    Page<Lead> findByStatusOrderByCreatedAtAsc(LeadStatus status, Pageable pageable);
    Page<Lead> findByCampaignIdOrderByCreatedAtDesc(Long campaignId, Pageable pageable);
    Page<Lead> findAllByOrderByCreatedAtDesc(Pageable pageable);
    long countByStatus(LeadStatus status);
    long countByUserIdAndCampaignId(Long userId, Long campaignId);
    long countByUserIdAndStatus(Long userId, LeadStatus status);
    // FIX: added — required by CampaignService to enforce totalLeadsCap
    long countByCampaignId(Long campaignId);
}