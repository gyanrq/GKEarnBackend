package com.myworld.modules.spin.web;

import com.myworld.core.dto.ApiResponse;
import com.myworld.core.security.CustomUserDetails;
import com.myworld.modules.spin.application.SpinService;
import com.myworld.modules.spin.infrastructure.SpinHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;

@RestController
@RequestMapping("/api/spin")
@RequiredArgsConstructor
public class SpinController {

    private final SpinService spinService;
    private final SpinHistoryRepository spinRepo;

    // ── Daily Spin ────────────────────────────────────────────────────────────

    @PostMapping("/daily")
    public ApiResponse<Map<String, Object>> dailySpin(
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        long credits = spinService.dailySpin(currentUser.getUser().getId());
        return ApiResponse.success(
            Map.of("creditsWon", credits, "message", "You won " + credits + " credits!"),
            "Spin successful");
    }

    // ── Spin Status (has user already spun today?) ────────────────────────────
    // Called on SpinPage mount so UI shows correct state without attempting a spin.

    @GetMapping("/status")
    public ApiResponse<Map<String, Object>> spinStatus(
            @AuthenticationPrincipal CustomUserDetails currentUser) {

        Long userId = currentUser.getUser().getId();
        OffsetDateTime todayIST = OffsetDateTime.now(ZoneOffset.ofHoursMinutes(5, 30))
                .toLocalDate().atStartOfDay().atOffset(ZoneOffset.ofHoursMinutes(5, 30));

        boolean spunToday = spinRepo.existsByUserIdAndCreatedAtAfter(userId, todayIST);

        Long creditsWon = spunToday
                ? spinRepo.findTopByUserIdAndCreatedAtAfterOrderByCreatedAtDesc(userId, todayIST)
                          .map(h -> h.getCreditsWon())
                          .orElse(null)
                : null;

        return ApiResponse.success(
            Map.of(
                "spunToday",  spunToday,
                "creditsWon", creditsWon != null ? creditsWon : 0
            ),
            "Spin status fetched");
    }
}