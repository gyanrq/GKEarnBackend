package com.myworld.modules.payout.web;

import com.myworld.core.dto.ApiResponse;
import com.myworld.core.dto.PageResponse;
import com.myworld.core.security.CustomUserDetails;
import com.myworld.modules.payout.api.PayoutDTO;
import com.myworld.modules.payout.application.PayoutService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
// FIX: Changed to "/api/payouts" to match React Frontend
@RequestMapping("/api/payouts")
@RequiredArgsConstructor
public class PayoutController {

    private final PayoutService payoutService;

    @PostMapping("/request")
    public ApiResponse<String> requestPayout(
            @Valid @RequestBody PayoutDTO dto,
            @AuthenticationPrincipal CustomUserDetails currentUser,
            jakarta.servlet.http.HttpServletRequest httpRequest) {
        payoutService.requestPayout(currentUser.getId(), dto, httpRequest);
        return ApiResponse.success("Payout request submitted successfully", "Done");
    }

    @GetMapping("/my")
    public ApiResponse<PageResponse<PayoutDTO>> getMyPayouts(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.success(
                payoutService.getUserPayouts(currentUser.getId(), page, size),
                "Payouts fetched");
    }
}