package com.myworld.modules.referral.web;

import com.myworld.core.dto.ApiResponse;
import com.myworld.core.dto.PageResponse;
import com.myworld.core.security.CustomUserDetails;
import com.myworld.modules.referral.api.ReferralResponseDTO;
import com.myworld.modules.referral.application.ReferralService;
import com.myworld.modules.referral.domain.MilestoneStatus;
import com.myworld.modules.referral.infrastructure.MilestoneStatusRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/referral")
@RequiredArgsConstructor
public class ReferralController {

    private final ReferralService referralService;
    private final MilestoneStatusRepository milestoneStatusRepo;

    @GetMapping("/my")
    public ApiResponse<PageResponse<ReferralResponseDTO>> getMyReferrals(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(
                referralService.getMyReferrals(currentUser.getUser().getId(), page, size),
                "Referrals fetched");
    }

    @GetMapping("/stats")
    public ApiResponse<Map<String, Object>> getMyStats(
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        Long userId = currentUser.getUser().getId();
        long successCount = referralService.countSuccessfulReferrals(userId);
        return ApiResponse.success(
                Map.of("successfulReferrals", successCount),
                "Stats fetched");
    }

    @GetMapping("/milestones")
    public ApiResponse<List<MilestoneStatus>> getMyMilestones(
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        List<MilestoneStatus> statuses = milestoneStatusRepo.findByUserId(
                currentUser.getUser().getId());
        return ApiResponse.success(statuses, "Milestones fetched");
    }
}
