package com.myworld.modules.admin.web;

import com.myworld.core.dto.ApiResponse;
import com.myworld.core.exception.BadRequestException;
import com.myworld.core.exception.ResourceNotFoundException;
import com.myworld.modules.admin.api.AdminSettingsDTO;
import com.myworld.modules.rewards.domain.RewardConfig;
import com.myworld.modules.rewards.infrastructure.RewardConfigRepository;
import com.myworld.modules.spin.domain.SpinPrize;
import com.myworld.modules.spin.infrastructure.SpinPrizeRepository;
import com.myworld.modules.tasks.domain.TaskConfig;
import com.myworld.modules.tasks.domain.TaskType;
import com.myworld.modules.tasks.infrastructure.TaskConfigRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/admin/settings")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN') or hasRole('MODERATOR')")
public class AdminSettingsController {

    private final RewardConfigRepository rewardConfigRepo;
    private final TaskConfigRepository taskConfigRepo;
    private final SpinPrizeRepository spinPrizeRepo;

    // ── REWARD CONFIG ─────────────────────────────────────────────────────────

    @GetMapping("/rewards")
    public ApiResponse<RewardConfig> getRewardConfig() {
        RewardConfig config = rewardConfigRepo.findFirstByIsActiveTrue()
                .orElseGet(RewardConfig::defaults);
        return ApiResponse.success(config, "Reward config fetched");
    }

    @PutMapping("/rewards")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<RewardConfig> updateRewardConfig(
            @Valid @RequestBody AdminSettingsDTO.RewardConfigRequest req) {

        // ── Window cross-validation (both set together or both null) ──────────
        boolean hasStart = req.getRedeemWindowStart() != null && !req.getRedeemWindowStart().isBlank();
        boolean hasEnd   = req.getRedeemWindowEnd()   != null && !req.getRedeemWindowEnd().isBlank();
        if (hasStart != hasEnd) {
            throw new BadRequestException(
                "Redeem window requires BOTH start and end times to be set, or leave both blank to disable.");
        }
        if (hasStart && req.getRedeemWindowStart().equals(req.getRedeemWindowEnd())) {
            throw new BadRequestException(
                "Redeem window start and end times cannot be the same.");
        }

        RewardConfig config = rewardConfigRepo.findFirstByIsActiveTrue()
                .orElseGet(RewardConfig::defaults);

        config.setCreditsPerRupee(req.getCreditsPerRupee());
        config.setMinRedeemCredits(req.getMinRedeemCredits());
        config.setMaxDailyEarn(req.getMaxDailyEarn());
        config.setRedemptionWaitSeconds(req.getRedemptionWaitSeconds());
        config.setPayoutProcessingSeconds(req.getPayoutProcessingSeconds());
        config.setIsActive(true);

        // Redeem time window — blank/null clears restriction
        config.setRedeemWindowStart(hasStart ? req.getRedeemWindowStart() : null);
        config.setRedeemWindowEnd(hasEnd     ? req.getRedeemWindowEnd()   : null);

        RewardConfig saved = rewardConfigRepo.save(config);

        log.info("[ADMIN] RewardConfig updated: creditsPerRupee={} minRedeem={} maxDailyEarn={} " +
                 "redemptionWaitSeconds={} payoutProcessingSeconds={} window={}-{}",
                req.getCreditsPerRupee(), req.getMinRedeemCredits(), req.getMaxDailyEarn(),
                req.getRedemptionWaitSeconds(), req.getPayoutProcessingSeconds(),
                req.getRedeemWindowStart(), req.getRedeemWindowEnd());

        return ApiResponse.success(saved, "Reward config updated successfully");
    }

    // ── TASK CONFIG ───────────────────────────────────────────────────────────

    @GetMapping("/tasks")
    public ApiResponse<List<Map<String, Object>>> getTaskConfigs() {
        Map<TaskType, TaskConfig> dbMap = taskConfigRepo.findAllByOrderByTaskTypeAsc()
                .stream().collect(Collectors.toMap(TaskConfig::getTaskType, c -> c));

        Map<TaskType, Long> defaults = Map.of(
            TaskType.LOGIN, 10L, TaskType.LEAD_SUBMIT, 50L,
            TaskType.SHARE_REFERRAL, 20L, TaskType.SPIN_WHEEL, 5L
        );
        Map<TaskType, String> labels = Map.of(
            TaskType.LOGIN, "Daily Login", TaskType.LEAD_SUBMIT, "Submit a Lead",
            TaskType.SHARE_REFERRAL, "Share Referral Link",
            TaskType.SPIN_WHEEL, "Spin the Wheel"
        );

        List<Map<String, Object>> result = Arrays.stream(TaskType.values()).map(type -> {
            TaskConfig cfg = dbMap.get(type);
            return Map.<String, Object>of(
                "id",       cfg != null ? cfg.getId() : "",
                "taskType", type.name(),
                "credits",  cfg != null ? cfg.getCredits() : defaults.getOrDefault(type, 0L),
                "label",    cfg != null && cfg.getLabel() != null ? cfg.getLabel() : labels.getOrDefault(type, type.name()),
                "isActive", cfg != null ? cfg.getIsActive() : true
            );
        }).collect(Collectors.toList());

        return ApiResponse.success(result, "Task configs fetched");
    }

    @PutMapping("/tasks")
    public ApiResponse<String> updateTaskConfigs(
            @Valid @RequestBody AdminSettingsDTO.TaskConfigRequest req) {
        for (AdminSettingsDTO.TaskConfigItem item : req.getTasks()) {
            TaskConfig cfg = taskConfigRepo.findByTaskType(item.getTaskType())
                    .orElseGet(() -> TaskConfig.builder().taskType(item.getTaskType()).build());
            cfg.setCredits(item.getCredits());
            if (item.getLabel() != null && !item.getLabel().isBlank()) cfg.setLabel(item.getLabel());
            if (item.getIsActive() != null) cfg.setIsActive(item.getIsActive());
            taskConfigRepo.save(cfg);
            log.info("[ADMIN] TaskConfig updated: type={} credits={}", item.getTaskType(), item.getCredits());
        }
        return ApiResponse.success("Task configs updated", "Done");
    }

    // ── SPIN CONFIG ───────────────────────────────────────────────────────────

    @GetMapping("/spin")
    public ApiResponse<List<SpinPrize>> getSpinPrizes() {
        return ApiResponse.success(spinPrizeRepo.findAllByOrderBySortOrderAsc(), "Spin prizes fetched");
    }

    @PutMapping("/spin")
    public ApiResponse<List<SpinPrize>> updateSpinPrizes(
            @Valid @RequestBody AdminSettingsDTO.SpinConfigRequest req) {
        List<SpinPrize> existing = spinPrizeRepo.findAllByOrderBySortOrderAsc();
        existing.forEach(p -> p.setIsActive(false));
        spinPrizeRepo.saveAll(existing);

        int order = 0;
        for (AdminSettingsDTO.SpinPrizeItem item : req.getPrizes()) {
            SpinPrize prize = item.getId() != null
                    ? spinPrizeRepo.findById(item.getId()).orElse(new SpinPrize())
                    : new SpinPrize();
            prize.setCredits(item.getCredits());
            prize.setWeight(item.getWeight());
            prize.setLabel(item.getLabel());
            prize.setColor(item.getColor());
            prize.setIsActive(item.getIsActive() != null ? item.getIsActive() : true);
            prize.setSortOrder(item.getSortOrder() != null ? item.getSortOrder() : order);
            spinPrizeRepo.save(prize);
            order++;
        }
        log.info("[ADMIN] SpinPrizes replaced: {} prizes", req.getPrizes().size());
        return ApiResponse.success(spinPrizeRepo.findAllByOrderBySortOrderAsc(), "Spin prizes updated");
    }

    @PostMapping("/spin")
    public ApiResponse<SpinPrize> addSpinPrize(
            @Valid @RequestBody AdminSettingsDTO.SpinPrizeItem item) {
        int maxOrder = spinPrizeRepo.findAllByOrderBySortOrderAsc().stream()
                .mapToInt(p -> p.getSortOrder() != null ? p.getSortOrder() : 0).max().orElse(0);
        SpinPrize prize = SpinPrize.builder()
                .credits(item.getCredits()).weight(item.getWeight())
                .label(item.getLabel()).color(item.getColor())
                .isActive(item.getIsActive() != null ? item.getIsActive() : true)
                .sortOrder(maxOrder + 1).build();
        return ApiResponse.success(spinPrizeRepo.save(prize), "Spin prize added");
    }

    @DeleteMapping("/spin/{id}")
    public ApiResponse<String> deleteSpinPrize(@PathVariable Long id) {
        if (!spinPrizeRepo.existsById(id))
            throw new ResourceNotFoundException("Spin prize not found: " + id);
        spinPrizeRepo.deleteById(id);
        log.info("[ADMIN] SpinPrize deleted: id={}", id);
        return ApiResponse.success("Spin prize deleted", "Done");
    }
}