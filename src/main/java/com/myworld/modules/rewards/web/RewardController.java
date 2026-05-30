package com.myworld.modules.rewards.web;

import com.myworld.core.dto.ApiResponse;
import com.myworld.core.dto.PageResponse;
import com.myworld.core.security.CustomUserDetails;
import com.myworld.modules.rewards.application.RewardService;
import com.myworld.modules.rewards.domain.RewardConfig;
import com.myworld.modules.rewards.domain.UserReward;
import com.myworld.modules.rewards.infrastructure.RewardConfigRepository;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * File: src/main/java/com/myworld/modules/rewards/web/RewardController.java
 *
 * CHANGE: getTransactions() now returns ApiResponse<PageResponse<RewardTransactionDTO>>
 * to match the updated RewardService interface — no raw entity ever reaches Jackson.
 */
@RestController
@RequestMapping("/api/rewards")
@RequiredArgsConstructor
public class RewardController {

    private final RewardService          rewardService;
    private final RewardConfigRepository rewardConfigRepo;

    // ── Public config DTO ─────────────────────────────────────────────────────
    @Getter
    @AllArgsConstructor
    public static class PublicRewardConfigDTO {
        private Integer creditsPerRupee;
        private Long    minRedeemCredits;
        private Long    maxDailyEarn;
        /** Account age gate in seconds — 0 means no restriction */
        private Long    redemptionWaitSeconds;
        /** Informational — shown to user as payout processing time in seconds */
        private Long    payoutProcessingSeconds;
        private String  redeemWindowStart;
        private String  redeemWindowEnd;
    }

    // ─────────────────────────────────────────────────────────────────────────

    @GetMapping("/balance")
    public ApiResponse<UserReward> getBalance(
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        return ApiResponse.success(
                rewardService.getBalance(currentUser.getUser().getId()),
                "Balance fetched");
    }

    /**
     * FIX: return type is now PageResponse<RewardTransactionDTO> — the DTO
     * contains no Hibernate associations so Jackson serialises it cleanly.
     * Previously returning PageResponse<RewardTransaction> caused:
     *   HttpMessageConversionException → ByteBuddyInterceptor (lazy User proxy)
     */
    @GetMapping("/transactions")
    public ApiResponse<PageResponse<RewardTransactionDTO>> getTransactions(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(
                rewardService.getTransactions(currentUser.getUser().getId(), page, size),
                "Transactions fetched");
    }

    @GetMapping("/config")
    public ApiResponse<PublicRewardConfigDTO> getRewardConfig() {
        RewardConfig config = rewardConfigRepo.findFirstByIsActiveTrue()
                .orElseGet(RewardConfig::defaults);
        return ApiResponse.success(
                new PublicRewardConfigDTO(
                        config.getCreditsPerRupee(),
                        config.getMinRedeemCredits(),
                        config.getMaxDailyEarn(),
                        config.getRedemptionWaitSeconds(),
                        config.getPayoutProcessingSeconds(),
                        config.getRedeemWindowStart(),
                        config.getRedeemWindowEnd()
                ),
                "Reward config fetched");
    }
}