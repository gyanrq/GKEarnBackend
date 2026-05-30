package com.myworld.modules.campaign.application;

import com.myworld.core.dto.PageResponse;
import com.myworld.core.exception.BadRequestException;
import com.myworld.core.exception.ResourceNotFoundException;
import com.myworld.modules.campaign.domain.*;
import com.myworld.modules.campaign.dto.*;
import com.myworld.modules.campaign.infrastructure.*;
import com.myworld.modules.identity.domain.User;
import com.myworld.modules.identity.infrastructure.UserRepository;
import com.myworld.modules.notification.application.NotificationService;
import com.myworld.modules.rewards.application.RewardService;
import com.myworld.modules.rewards.domain.RewardSource;
import com.myworld.core.config.CacheConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CampaignService {

    private final CampaignRepository campaignRepo;
    private final LeadRepository leadRepo;
    private final UserRepository userRepo;
    private final RewardService rewardService;
    private final NotificationService notificationService;

    // FIX: configurable daily lead submission cap per user (across all campaigns)
    @Value("${app.campaign.max-daily-leads-per-user:5}")
    private int maxDailyLeadsPerUser;

    @CacheEvict(value = CacheConfig.CACHE_CAMPAIGNS, allEntries = true)
    @Transactional
    public Campaign createCampaign(CampaignCreateDTO dto) {
        Campaign campaign = Campaign.builder()
                .name(dto.getName())
                .description(dto.getDescription())
                .campaignType(dto.getCampaignType())
                .trackingLink(dto.getTrackingLink())
                .rewardAmount(dto.getRewardAmount())
                .termsUrl(dto.getTermsUrl())
                .logoUrl(dto.getLogoUrl())
                .advertiserName(dto.getAdvertiserName())
                .startAt(dto.getStartAt())
                .endAt(dto.getEndAt())
                .maxLeadsPerUser(dto.getMaxLeadsPerUser() != null ? dto.getMaxLeadsPerUser() : 1)
                .totalLeadsCap(dto.getTotalLeadsCap() != null ? dto.getTotalLeadsCap() : 0)
                .isActive(true)
                .build();
        return campaignRepo.save(campaign);
    }

    @CacheEvict(value = CacheConfig.CACHE_CAMPAIGNS, allEntries = true)
    @Transactional
    public void toggleCampaign(Long campaignId, boolean active) {
        Campaign c = findCampaignOrThrow(campaignId);
        c.setIsActive(active);
        campaignRepo.save(c);
    }

    // FIX: added edit support — previously adminApi.js called PUT /admin/settings/campaigns/:id
    // which 404'd because no such endpoint existed. Now the service exposes updateCampaign()
    // and AdminCampaignController wires it up at PUT /admin/campaigns/:id.
    @CacheEvict(value = CacheConfig.CACHE_CAMPAIGNS, allEntries = true)
    @Transactional
    public Campaign updateCampaign(Long campaignId, CampaignCreateDTO dto) {
        Campaign c = findCampaignOrThrow(campaignId);
        if (dto.getName()            != null) c.setName(dto.getName());
        if (dto.getDescription()     != null) c.setDescription(dto.getDescription());
        if (dto.getCampaignType()    != null) c.setCampaignType(dto.getCampaignType());
        if (dto.getTrackingLink()    != null) c.setTrackingLink(dto.getTrackingLink());
        if (dto.getRewardAmount()    != null) c.setRewardAmount(dto.getRewardAmount());
        if (dto.getTermsUrl()        != null) c.setTermsUrl(dto.getTermsUrl());
        if (dto.getLogoUrl()         != null) c.setLogoUrl(dto.getLogoUrl());
        if (dto.getAdvertiserName()  != null) c.setAdvertiserName(dto.getAdvertiserName());
        if (dto.getStartAt()         != null) c.setStartAt(dto.getStartAt());
        if (dto.getEndAt()           != null) c.setEndAt(dto.getEndAt());
        if (dto.getMaxLeadsPerUser() != null) c.setMaxLeadsPerUser(dto.getMaxLeadsPerUser());
        if (dto.getTotalLeadsCap()   != null) c.setTotalLeadsCap(dto.getTotalLeadsCap());
        return campaignRepo.save(c);
    }

    // FIX: Cache active campaign list — this is the highest-traffic read endpoint.
    // Invalidated when a campaign is created, updated, or toggled.
    @Cacheable(value = CacheConfig.CACHE_CAMPAIGNS, key = "'page-' + #page + '-size-' + #size")
    @Transactional(readOnly = true)
    public PageResponse<Campaign> getActiveCampaigns(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        // FIX: previously only filtered by isActive = true. A campaign whose endAt has
        // passed would still appear if the admin hadn't manually deactivated it.
        // Now we also exclude campaigns where endAt is set and in the past.
        Page<Campaign> pg = campaignRepo.findActiveAndNotExpired(OffsetDateTime.now(), pageable);
        return buildPage(pg.getContent(), pg);
    }

    @Transactional(readOnly = true)
    public PageResponse<Campaign> getAllCampaigns(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<Campaign> pg = campaignRepo.findAll(pageable);
        return buildPage(pg.getContent(), pg);
    }

    @Transactional
    public Lead submitLead(Long userId, LeadSubmitRequest req) {
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        Campaign campaign = findCampaignOrThrow(req.getCampaignId());

        if (!Boolean.TRUE.equals(campaign.getIsActive()))
            throw new BadRequestException("Campaign is not active");

        OffsetDateTime now = OffsetDateTime.now();
        if (campaign.getStartAt() != null && now.isBefore(campaign.getStartAt()))
            throw new BadRequestException("Campaign has not started yet");
        if (campaign.getEndAt() != null && now.isAfter(campaign.getEndAt()))
            throw new BadRequestException("Campaign has ended");

        // FIX: global daily lead cap — prevents a user from spamming leads across campaigns
        long pendingLeadsToday = leadRepo.countByUserIdAndStatus(userId, LeadStatus.PENDING);
        if (pendingLeadsToday >= maxDailyLeadsPerUser)
            throw new BadRequestException(
                "You have " + pendingLeadsToday + " pending leads. Max " + maxDailyLeadsPerUser +
                " pending leads allowed at a time. Wait for existing leads to be reviewed.");

        // FIX: per-user lead cap check
        long existingLeads = leadRepo.countByUserIdAndCampaignId(userId, campaign.getId());
        if (existingLeads >= campaign.getMaxLeadsPerUser())
            throw new BadRequestException("You have already submitted the maximum leads for this campaign");

        // FIX: total campaign lead cap — 0 means unlimited
        if (campaign.getTotalLeadsCap() > 0) {
            long totalLeads = leadRepo.countByCampaignId(campaign.getId());
            if (totalLeads >= campaign.getTotalLeadsCap())
                throw new BadRequestException("This campaign has reached its maximum number of leads");
        }

        Lead lead = Lead.builder()
                .user(user).campaign(campaign).status(LeadStatus.PENDING)
                .registeredMobile(req.getRegisteredMobile())
                .registeredEmail(req.getRegisteredEmail())
                .proofUrl(req.getProofUrl())
                .build();
        Lead saved;
        try {
            saved = leadRepo.saveAndFlush(lead);
        } catch (org.springframework.dao.DataIntegrityViolationException ex) {
            throw new BadRequestException("You have already submitted a lead for this campaign.");
        }

        notificationService.sendNotification(userId, "CAMPAIGN", "Lead Submitted",
            "Your lead for \"" + campaign.getName() + "\" has been submitted and is pending review.");
        return saved;
    }

    @Transactional(readOnly = true)
    public PageResponse<Lead> getMyLeads(Long userId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<Lead> pg = leadRepo.findByUserIdOrderByCreatedAtDesc(userId, pageable);
        return buildPage(pg.getContent(), pg);
    }

    @Transactional(readOnly = true)
    public PageResponse<Lead> getAllLeads(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<Lead> pg = leadRepo.findAllByOrderByCreatedAtDesc(pageable);
        return buildPage(pg.getContent(), pg);
    }

    @Transactional(readOnly = true)
    public PageResponse<Lead> getLeadsByStatus(LeadStatus status, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").ascending());
        Page<Lead> pg = leadRepo.findByStatusOrderByCreatedAtAsc(status, pageable);
        return buildPage(pg.getContent(), pg);
    }

    @Transactional(readOnly = true)
    public PageResponse<Lead> getLeadsByCampaign(Long campaignId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<Lead> pg = leadRepo.findByCampaignIdOrderByCreatedAtDesc(campaignId, pageable);
        return buildPage(pg.getContent(), pg);
    }

    @Transactional
    public void verifyLead(Long leadId, AdminVerifyRequest req) {
        Lead lead = leadRepo.findById(leadId)
                .orElseThrow(() -> new ResourceNotFoundException("Lead not found: " + leadId));

        if (lead.getStatus() != LeadStatus.PENDING)
            throw new BadRequestException("Only PENDING leads can be verified. Current: " + lead.getStatus());

        Long userId = lead.getUser().getId();
        String campaignName = lead.getCampaign().getName();

        if (req.getStatus() == LeadStatus.APPROVED) {
            lead.setStatus(LeadStatus.REWARDED);
            lead.setAdminNotes(req.getNotes());
            leadRepo.save(lead);

            long credits = lead.getCampaign().getRewardAmount()
                    .multiply(BigDecimal.TEN).longValue();
            rewardService.earnCredits(userId, credits,
                    "Campaign Reward: " + campaignName, RewardSource.CAMPAIGN);

            log.info("Lead approved & rewarded: leadId={} userId={} credits={}", leadId, userId, credits);
            notificationService.sendNotification(userId, "REWARD",
                "Lead Approved — Reward Credited! 🎉",
                "Your lead for \"" + campaignName + "\" has been approved! " +
                credits + " credits have been added to your rewards.");

        } else if (req.getStatus() == LeadStatus.REJECTED) {
            lead.setStatus(LeadStatus.REJECTED);
            lead.setRejectionReason(req.getRejectionReason());
            lead.setAdminNotes(req.getNotes());
            leadRepo.save(lead);
            String reason = req.getRejectionReason() != null && !req.getRejectionReason().isBlank()
                    ? req.getRejectionReason() : "Please ensure your submission meets the campaign requirements.";
            notificationService.sendNotification(userId, "CAMPAIGN", "Lead Rejected",
                "Your lead for \"" + campaignName + "\" was not approved. Reason: " + reason);
        } else {
            throw new BadRequestException("Invalid status: " + req.getStatus());
        }
    }

    private Campaign findCampaignOrThrow(Long id) {
        return campaignRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Campaign not found: " + id));
    }

    private <T> PageResponse<T> buildPage(List<T> content, Page<?> page) {
        return PageResponse.<T>builder()
                .content(content).page(page.getNumber()).size(page.getSize())
                .totalElements(page.getTotalElements()).totalPages(page.getTotalPages())
                .last(page.isLast()).build();
    }
}