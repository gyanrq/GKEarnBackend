package com.myworld.modules.campaign.web;

import com.myworld.core.dto.ApiResponse;
import com.myworld.core.dto.PageResponse;
import com.myworld.modules.campaign.application.CampaignService;
import com.myworld.modules.campaign.domain.Campaign;
import com.myworld.modules.campaign.dto.CampaignCreateDTO;
import com.myworld.modules.campaign.dto.CampaignResponseDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/campaigns")
@RequiredArgsConstructor
public class AdminCampaignController {

    private final CampaignService campaignService;

    // FIX: returns CampaignResponseDTO instead of raw Campaign JPA entity.
    // Returning entities directly couples the API contract to the DB schema — any field
    // added to Campaign (e.g. internal flags) immediately leaks to the API response.
    @PostMapping
    public ApiResponse<CampaignResponseDTO> createCampaign(@Valid @RequestBody CampaignCreateDTO dto) {
        return ApiResponse.success(CampaignResponseDTO.from(campaignService.createCampaign(dto)), "Campaign created");
    }

    @GetMapping
    public ApiResponse<PageResponse<CampaignResponseDTO>> getAllCampaigns(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {
        PageResponse<Campaign> raw = campaignService.getAllCampaigns(page, size);
        PageResponse<CampaignResponseDTO> response = PageResponse.<CampaignResponseDTO>builder()
                .content(raw.getContent().stream().map(CampaignResponseDTO::from).toList())
                .page(raw.getPage())
                .size(raw.getSize())
                .totalElements(raw.getTotalElements())
                .totalPages(raw.getTotalPages())
                .last(raw.isLast())
                .build();
        return ApiResponse.success(response, "Campaigns fetched");
    }

    // FIX: added PUT /{campaignId} endpoint for editing campaigns.
    // adminApi.js called PUT /admin/settings/campaigns/:id (wrong path) which 404'd.
    // This endpoint now matches PUT /admin/campaigns/:id which is the correct path
    // under this controller's /api/admin/campaigns base mapping.
    // adminApi.js editCampaign must also be updated to call /admin/campaigns/:id.
    @PutMapping("/{campaignId}")
    public ApiResponse<CampaignResponseDTO> editCampaign(
            @PathVariable Long campaignId,
            @Valid @RequestBody CampaignCreateDTO dto) {
        return ApiResponse.success(
                CampaignResponseDTO.from(campaignService.updateCampaign(campaignId, dto)),
                "Campaign updated");
    }

    @PostMapping("/{campaignId}/activate")
    public ApiResponse<String> activate(@PathVariable Long campaignId) {
        campaignService.toggleCampaign(campaignId, true);
        return ApiResponse.success("Campaign activated", "Done");
    }

    @PostMapping("/{campaignId}/deactivate")
    public ApiResponse<String> deactivate(@PathVariable Long campaignId) {
        campaignService.toggleCampaign(campaignId, false);
        return ApiResponse.success("Campaign deactivated", "Done");
    }
}