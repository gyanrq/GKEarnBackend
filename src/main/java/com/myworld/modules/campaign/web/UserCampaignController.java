package com.myworld.modules.campaign.web;

import com.myworld.core.dto.ApiResponse;
import com.myworld.core.dto.PageResponse;
import com.myworld.core.security.CustomUserDetails;
import com.myworld.modules.campaign.application.CampaignService;
import com.myworld.modules.campaign.domain.Campaign;
import com.myworld.modules.campaign.domain.Lead;
import com.myworld.modules.campaign.dto.CampaignResponseDTO;
import com.myworld.modules.campaign.dto.LeadResponseDTO;
import com.myworld.modules.campaign.dto.LeadSubmitRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * FIX (Issue #3): All three endpoints now return DTOs instead of raw JPA entities.
 *
 * Previously, getActiveCampaigns() returned PageResponse<Campaign> and getMyLeads()
 * returned PageResponse<Lead>. Both entities have LAZY-loaded associations:
 *   - Campaign has no lazy relations but returning the entity leaks internal DB fields.
 *   - Lead.user and Lead.campaign are LAZY @ManyToOne. Once the Hibernate session
 *     closes after the @Transactional service method returns, Jackson tries to
 *     serialise them and Hibernate throws LazyInitializationException.
 *     Lead also has a Lead → User → [other relations] risk for recursive loops.
 *
 * Fix: Map Campaign → CampaignResponseDTO and Lead → LeadResponseDTO (both DTOs
 * already existed and were used in the admin controllers — just not here).
 *
 * submitLead() still returns LeadResponseDTO for the same reason.
 */
@RestController
@RequestMapping("/api/campaigns")
@RequiredArgsConstructor
public class UserCampaignController {

    private final CampaignService campaignService;

    // ── Active campaigns list ─────────────────────────────────────────────────

    /**
     * GET /api/campaigns?page=0&size=20
     * Returns active, non-expired campaigns.
     *
     * FIX: returns PageResponse<CampaignResponseDTO> instead of raw Campaign entity.
     */
    @GetMapping
    public ApiResponse<PageResponse<CampaignResponseDTO>> getActiveCampaigns(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {

        PageResponse<Campaign> raw = campaignService.getActiveCampaigns(page, size);

        PageResponse<CampaignResponseDTO> response = PageResponse.<CampaignResponseDTO>builder()
                .content(raw.getContent().stream().map(CampaignResponseDTO::from).toList())
                .page(raw.getPage())
                .size(raw.getSize())
                .totalElements(raw.getTotalElements())
                .totalPages(raw.getTotalPages())
                .last(raw.isLast())
                .build();

        return ApiResponse.success(response, "Active campaigns fetched");
    }

    // ── Submit lead ───────────────────────────────────────────────────────────

    /**
     * POST /api/campaigns/leads/submit
     * Body: LeadSubmitRequest { campaignId, registeredMobile?, registeredEmail?, proofUrl? }
     *
     * FIX: returns LeadResponseDTO instead of raw Lead entity.
     * Raw Lead has LAZY Lead.user and Lead.campaign — both would throw
     * LazyInitializationException when Jackson tries to serialise them
     * after the Hibernate session has closed.
     */
    @PostMapping("/leads/submit")
    public ApiResponse<LeadResponseDTO> submitLead(
            @Valid @RequestBody LeadSubmitRequest req,
            @AuthenticationPrincipal CustomUserDetails currentUser) {

        Lead saved = campaignService.submitLead(currentUser.getUser().getId(), req);
        return ApiResponse.success(LeadResponseDTO.from(saved), "Lead submitted successfully");
    }

    // ── My leads ──────────────────────────────────────────────────────────────

    /**
     * GET /api/campaigns/leads/my?page=0&size=20
     *
     * FIX: returns PageResponse<LeadResponseDTO> instead of raw Lead entity.
     * LeadResponseDTO.from() eagerly accesses lead.getUser() and lead.getCampaign()
     * inside the @Transactional service method where the session is still open,
     * flattening them into scalar fields before the session closes.
     */
    @GetMapping("/leads/my")
    public ApiResponse<PageResponse<LeadResponseDTO>> getMyLeads(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {

        PageResponse<Lead> raw = campaignService.getMyLeads(currentUser.getUser().getId(), page, size);

        PageResponse<LeadResponseDTO> response = PageResponse.<LeadResponseDTO>builder()
                .content(raw.getContent().stream().map(LeadResponseDTO::from).toList())
                .page(raw.getPage())
                .size(raw.getSize())
                .totalElements(raw.getTotalElements())
                .totalPages(raw.getTotalPages())
                .last(raw.isLast())
                .build();

        return ApiResponse.success(response, "Your leads fetched");
    }
}