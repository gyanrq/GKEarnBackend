package com.myworld.modules.admin.web;

import com.myworld.core.constant.Role;
import com.myworld.core.dto.ApiResponse;
import com.myworld.core.dto.PageResponse;
import com.myworld.modules.admin.api.*;
import com.myworld.modules.admin.application.AdminDashboardService;
import com.myworld.modules.admin.application.AdminUserAnalyticsService;
import com.myworld.modules.admin.application.AdminUserService;
import com.myworld.modules.identity.api.UserResponseDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminUserService       adminUserService;
    private final AdminDashboardService  dashboardService;
    private final AdminUserAnalyticsService userAnalyticsService;

    // ── Dashboard ─────────────────────────────────────────────────────────────
    @GetMapping("/dashboard")
    public ApiResponse<AdminDashboardDTO> getDashboard() {
        return ApiResponse.success(dashboardService.getDashboard(), "Dashboard fetched");
    }

    // ── Financial Oversight endpoints (NEW) ───────────────────────────────────
    @GetMapping("/analytics/risk")
    public ApiResponse<RiskSafetyDTO> getRiskAnalytics() {
        return ApiResponse.success(dashboardService.getRiskAnalytics(), "Risk analytics fetched");
    }

    @GetMapping("/analytics/earnings-by-source")
    public ApiResponse<List<EarningsBySourceDTO>> getEarningsBySource() {
        return ApiResponse.success(dashboardService.getEarningsBySource(), "Earnings by source fetched");
    }

    @GetMapping("/analytics/task-economy")
    public ApiResponse<List<TaskRewardSummaryDTO>> getTaskEconomy() {
        return ApiResponse.success(dashboardService.getTaskEconomySummary(), "Task economy fetched");
    }

    @GetMapping("/analytics/lead-rewards")
    public ApiResponse<List<LeadRewardSummaryDTO>> getLeadRewards() {
        return ApiResponse.success(dashboardService.getLeadRewardSummary(), "Lead rewards fetched");
    }

    // ── User Management ───────────────────────────────────────────────────────
    @GetMapping("/users")
    public ApiResponse<PageResponse<UserResponseDTO>> searchUsers(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Role role,
            @RequestParam(required = false) Boolean isBlocked,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(
                adminUserService.searchUsers(q, role, isBlocked, page, size), "Users fetched");
    }

    @GetMapping("/users/{userId}")
    public ApiResponse<UserResponseDTO> getUserDetail(@PathVariable Long userId) {
        return ApiResponse.success(adminUserService.getUserDetail(userId), "User detail fetched");
    }

    @PostMapping("/users/{userId}/block")
    public ApiResponse<String> blockUser(@PathVariable Long userId,
                                         @RequestBody AdminUserActionDTO dto) {
        adminUserService.blockUser(userId, dto);
        return ApiResponse.success("User blocked successfully", "Done");
    }

    @PostMapping("/users/{userId}/unblock")
    public ApiResponse<String> unblockUser(@PathVariable Long userId) {
        adminUserService.unblockUser(userId);
        return ApiResponse.success("User unblocked successfully", "Done");
    }

    @DeleteMapping("/users/{userId}")
    public ApiResponse<String> deleteUser(@PathVariable Long userId) {
        adminUserService.deleteUser(userId);
        return ApiResponse.success("User deleted successfully", "Done");
    }

    @PutMapping("/users/{userId}/role")
    public ApiResponse<String> changeRole(@PathVariable Long userId,
                                          @Valid @RequestBody AdminChangeRoleDTO dto) {
        adminUserService.changeRole(userId, dto);
        return ApiResponse.success("Role updated successfully", "Done");
    }

    @PostMapping("/users/{userId}/notes")
    public ApiResponse<String> addNote(@PathVariable Long userId,
                                       @RequestBody AdminUserActionDTO dto) {
        adminUserService.addAdminNote(userId, dto);
        return ApiResponse.success("Note saved", "Done");
    }

    // ── Credits Management ────────────────────────────────────────────────────
    @PostMapping("/credits/{userId}/credit")
    public ApiResponse<String> creditCredits(@PathVariable Long userId,
                                             @Valid @RequestBody AdminCreditActionDTO dto) {
        adminUserService.creditUserCredits(userId, dto);
        return ApiResponse.success("Credits added successfully", "Done");
    }

    @PostMapping("/credits/{userId}/debit")
    public ApiResponse<String> debitCredits(@PathVariable Long userId,
                                            @Valid @RequestBody AdminCreditActionDTO dto) {
        adminUserService.debitUserCredits(userId, dto);
        return ApiResponse.success("Credits deducted successfully", "Done");
    }
    
    // ── User 360° Deep Analytics ──────────────────────────────────────────────
    @GetMapping("/users/{userId}/analytics")
    public ApiResponse<UserAnalyticsDTO> getUserDeepAnalytics(@PathVariable Long userId) {
        return ApiResponse.success(
                userAnalyticsService.getUserDeepAnalytics(userId),
                "User analytics fetched"
        );
    }
 
    // ── Admin Action: Reset Password ──────────────────────────────────────────
    @PostMapping("/users/{userId}/reset-password")
    public ApiResponse<String> resetUserPassword(@PathVariable Long userId,
                                                  @RequestBody AdminUserActionDTO dto) {
        adminUserService.resetUserPassword(userId, dto);
        return ApiResponse.success("Password reset successfully", "Done");
    }
 
    // ── Admin Action: Mark as Fraud ────────────────────────────────────────────
    @PostMapping("/users/{userId}/mark-fraud")
    public ApiResponse<String> markAsFraud(@PathVariable Long userId,
                                            @RequestBody AdminUserActionDTO dto) {
        adminUserService.markAsFraud(userId, dto);
        return ApiResponse.success("User marked as fraud", "Done");
    }
 
        
        
        
        
        
        
        
        
        
        
}
