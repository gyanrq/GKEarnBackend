package com.myworld.modules.rewards.web;

import com.myworld.core.dto.ApiResponse;
import com.myworld.modules.rewards.infrastructure.UserRewardRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
public class PublicRewardController {

    private final UserRewardRepository rewardRepository;

    @GetMapping("/leaderboard")
    public ApiResponse<List<LeaderboardDTO>> getLeaderboard(
            @RequestParam(defaultValue = "10") int limit) {
        if (limit > 50) limit = 50;
        List<LeaderboardDTO> topEarners = rewardRepository
                .findTopEarnersDto(PageRequest.of(0, limit))
                .stream()
                .map(dto -> new LeaderboardDTO(
                        dto.getUserId(),
                        maskName(dto.getEmail()),
                        dto.getTotalEarned(),
                        dto.getNetBalance()
                ))
                .collect(Collectors.toList());
        return ApiResponse.success(topEarners, "Leaderboard fetched");
    }

    private String maskName(String email) {
        if (email == null) return "User***";
        int at = email.indexOf('@');
        if (at <= 1) return "U***";
        return email.substring(0, Math.min(3, at)) + "***";
    }

    @Data
    public static class LeaderboardDTO {
        private final Long userId;
        private final String displayName;
        private final Long totalCredits;
        private final Long balance;
    }
}
