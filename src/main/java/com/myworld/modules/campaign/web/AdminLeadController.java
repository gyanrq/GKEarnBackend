package com.myworld.modules.campaign.web;

import com.myworld.core.dto.ApiResponse;
import com.myworld.core.dto.PageResponse;
import com.myworld.modules.campaign.application.CampaignService;
import com.myworld.modules.campaign.domain.Lead;
import com.myworld.modules.campaign.domain.LeadStatus;
import com.myworld.modules.campaign.dto.AdminVerifyRequest;
import com.myworld.modules.campaign.dto.LeadResponseDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/leads")
@RequiredArgsConstructor
public class AdminLeadController {

    private final CampaignService campaignService;

    // FIX: all endpoints now return PageResponse<LeadResponseDTO> instead of
    // PageResponse<Lead>. The raw Lead entity has LAZY-loaded user and campaign
    // relations — serializing them after the transaction closes throws
    // LazyInitializationException. LeadResponseDTO.from() eagerly projects all
    // needed fields while the session is still open inside the service call.
    @GetMapping
    public ApiResponse<PageResponse<LeadResponseDTO>> getAllLeads(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(toDTO(campaignService.getAllLeads(page, size)), "Leads fetched");
    }

    @GetMapping("/status/{status}")
    public ApiResponse<PageResponse<LeadResponseDTO>> getByStatus(
            @PathVariable LeadStatus status,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(toDTO(campaignService.getLeadsByStatus(status, page, size)), "Leads fetched");
    }

    @GetMapping("/campaign/{campaignId}")
    public ApiResponse<PageResponse<LeadResponseDTO>> getByCampaign(
            @PathVariable Long campaignId,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(toDTO(campaignService.getLeadsByCampaign(campaignId, page, size)), "Leads fetched");
    }

    @PostMapping("/{leadId}/verify")
    public ApiResponse<String> verifyLead(@PathVariable Long leadId,
                                          @Valid @RequestBody AdminVerifyRequest req) {
        campaignService.verifyLead(leadId, req);
        return ApiResponse.success("Lead " + req.getStatus().name().toLowerCase() + " successfully", "Done");
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private PageResponse<LeadResponseDTO> toDTO(PageResponse<Lead> raw) {
        return PageResponse.<LeadResponseDTO>builder()
                .content(raw.getContent().stream().map(LeadResponseDTO::from).toList())
                .page(raw.getPage())
                .size(raw.getSize())
                .totalElements(raw.getTotalElements())
                .totalPages(raw.getTotalPages())
                .last(raw.isLast())
                .build();
    }
}