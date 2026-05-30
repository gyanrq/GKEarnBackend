package com.myworld.modules.payout.web;

import com.myworld.core.dto.ApiResponse;
import com.myworld.core.dto.PageResponse;
import com.myworld.modules.payout.api.PayoutDTO;
import com.myworld.modules.payout.application.PayoutService;
import com.myworld.modules.payout.domain.PaymentStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/payouts")
@RequiredArgsConstructor
public class AdminPayoutController {

    private final PayoutService payoutService;

    @GetMapping
    public ApiResponse<PageResponse<PayoutDTO>> getAllPayouts(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(payoutService.getAllPayouts(page, size), "Payouts fetched");
    }

    // FIX: @PathVariable now maps to PaymentStatus enum — Spring auto-converts the string.
    // Invalid values (e.g. /status/BLAH) return 400 automatically instead of hitting the DB.
    @GetMapping("/status/{status}")
    public ApiResponse<PageResponse<PayoutDTO>> getByStatus(
            @PathVariable PaymentStatus status,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(payoutService.getPayoutsByStatus(status, page, size),
                "Payouts fetched");
    }

    @PostMapping("/{payoutId}/approve")
    public ApiResponse<String> approve(@PathVariable Long payoutId, Authentication auth) {
        payoutService.approvePayout(payoutId, auth.getName());
        return ApiResponse.success("Payout approved", "Done");
    }

    @PostMapping("/{payoutId}/reject")
    public ApiResponse<String> reject(@PathVariable Long payoutId,
                                      @RequestBody Map<String, String> body,
                                      Authentication auth) {
        payoutService.rejectPayout(payoutId, body.getOrDefault("reason", "Rejected by admin"),
                auth.getName());
        return ApiResponse.success("Payout rejected and credits refunded", "Done");
    }

    @PostMapping("/{payoutId}/mark-paid")
    public ApiResponse<String> markPaid(@PathVariable Long payoutId,
                                        @RequestBody Map<String, String> body,
                                        Authentication auth) {
        payoutService.markPaid(payoutId, body.get("transactionRef"), auth.getName());
        return ApiResponse.success("Payout marked as PAID", "Done");
    }
}