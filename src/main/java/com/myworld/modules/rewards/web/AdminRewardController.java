/*
 * package com.myworld.modules.rewards.web;
 * 
 * import com.myworld.core.dto.ApiResponse; import
 * com.myworld.modules.rewards.application.RewardService; import
 * lombok.RequiredArgsConstructor; import
 * org.springframework.security.access.prepost.PreAuthorize; import
 * org.springframework.web.bind.annotation.*;
 * 
 * @RestController
 * 
 * @RequestMapping("/api/admin/rewards")
 * 
 * @RequiredArgsConstructor
 * 
 * @PreAuthorize("hasRole('ADMIN')") // FIXED: Only ADMIN can credit/debit user
 * accounts public class AdminRewardController {
 * 
 * private final RewardService rewardService;
 * 
 * @PostMapping("/{userId}/credit") public ApiResponse<String>
 * credit(@PathVariable Long userId,
 * 
 * @RequestParam Long credits,
 * 
 * @RequestParam String reason) { rewardService.adminCredit(userId, credits,
 * reason); return ApiResponse.success("OK", "Credits added"); }
 * 
 * @PostMapping("/{userId}/debit") public ApiResponse<String>
 * debit(@PathVariable Long userId,
 * 
 * @RequestParam Long credits,
 * 
 * @RequestParam String reason) { rewardService.adminDebit(userId, credits,
 * reason); return ApiResponse.success("OK", "Credits deducted"); } }
 */