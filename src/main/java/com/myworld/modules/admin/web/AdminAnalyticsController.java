package com.myworld.modules.admin.web;

import com.myworld.core.dto.ApiResponse;
import com.myworld.modules.admin.application.AdminDashboardService;
import com.myworld.modules.admin.api.*;
import com.myworld.modules.identity.infrastructure.UserRepository;
import com.myworld.modules.rewards.infrastructure.RewardTransactionRepository;
import com.myworld.modules.payout.infrastructure.PayoutRepository;
import com.myworld.modules.payout.domain.PaymentStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

@RestController
@RequestMapping("/api/admin/analytics")
@RequiredArgsConstructor
public class AdminAnalyticsController {

    private final AdminDashboardService dashboardService;
    private final UserRepository userRepo;
    private final RewardTransactionRepository rewardTxRepo;
    private final PayoutRepository payoutRepo;

    /**
     * GET /api/admin/analytics/overview
     * Returns full admin dashboard stats
     */
    @GetMapping("/overview")
    public ApiResponse<AdminDashboardDTO> getOverview() {
        return ApiResponse.success(dashboardService.getDashboard(), "Analytics fetched");
    }

    /**
     * GET /api/admin/analytics/daily-users?days=30
     * Daily new user registrations chart data
     */
    @GetMapping("/daily-users")
    public ApiResponse<List<Map<String, Object>>> getDailyUsers(
            @RequestParam(defaultValue = "30") int days) {
        List<Map<String, Object>> result = new ArrayList<>();
        ZoneOffset ist = ZoneOffset.ofHoursMinutes(5, 30);
        for (int i = days - 1; i >= 0; i--) {
            LocalDate date = LocalDate.now(ist).minusDays(i);
            OffsetDateTime start = date.atStartOfDay().atOffset(ist);
            OffsetDateTime end   = date.plusDays(1).atStartOfDay().atOffset(ist);
            long count = userRepo.countByCreatedAtBetween(start, end);
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("date", date.toString());
            entry.put("newUsers", count);
            result.add(entry);
        }
        return ApiResponse.success(result, "Daily users fetched");
    }

    /**
     * GET /api/admin/analytics/top-earners?limit=10
     */
    @GetMapping("/top-earners")
    public ApiResponse<List<TopEarnerDTO>> getTopEarners() {
        return ApiResponse.success(
            dashboardService.getTopEarners(), "Top earners fetched");
    }

    /**
     * GET /api/admin/analytics/payout-summary
     */
    @GetMapping("/payout-summary")
    public ApiResponse<Map<String, Object>> getPayoutSummary() {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("pending",  payoutRepo.countByStatus(PaymentStatus.PENDING));
        summary.put("approved", payoutRepo.countByStatus(PaymentStatus.APPROVED));
        summary.put("paid",     payoutRepo.countByStatus(PaymentStatus.PAID));
        summary.put("rejected", payoutRepo.countByStatus(PaymentStatus.REJECTED));
        summary.put("processing", payoutRepo.countByStatus(PaymentStatus.PROCESSING));
        return ApiResponse.success(summary, "Payout summary fetched");
    }
}
