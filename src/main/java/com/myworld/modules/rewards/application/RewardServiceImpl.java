package com.myworld.modules.rewards.application;

import com.myworld.core.dto.PageResponse;
import com.myworld.core.exception.BadRequestException;
import com.myworld.core.exception.ResourceNotFoundException;
import com.myworld.modules.admin.domain.AdminAuditLog;
import com.myworld.modules.admin.infrastructure.AdminAuditRepository;
import com.myworld.modules.identity.domain.User;
import com.myworld.modules.identity.infrastructure.UserRepository;
import com.myworld.modules.rewards.domain.*;
import com.myworld.modules.rewards.infrastructure.*;
import com.myworld.modules.rewards.web.RewardTransactionDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * File: src/main/java/com/myworld/modules/rewards/application/RewardServiceImpl.java
 *
 * KEY FIX in getTransactions():
 *   - Maps Page<RewardTransaction> → List<RewardTransactionDTO> INSIDE the
 *     @Transactional boundary, while the Hibernate session is still open.
 *   - RewardTransactionDTO.from(tx) reads only primitive/enum fields — it
 *     never touches tx.getUser(), so the lazy proxy is never initialized
 *     and Jackson never sees a ByteBuddyInterceptor.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RewardServiceImpl implements RewardService {

    private final UserRewardRepository        rewardRepo;
    private final RewardTransactionRepository txRepo;
    private final RewardConfigRepository      configRepo;
    private final UserRepository              userRepo;
    private final AdminAuditRepository        adminAuditRepo;

    @Value("${app.timezone:+05:30}")
    private String timezoneOffset;

    // ── Earn ─────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public void earnCredits(Long userId, Long credits, String description, RewardSource source) {
        validateCredits(credits);
        RewardConfig config = getActiveConfig();
        checkDailyEarnCap(userId, credits, config.getMaxDailyEarn());

        UserReward reward = getLockedReward(userId);
        reward.setTotalCredits(reward.getTotalCredits() + credits);
        rewardRepo.save(reward);

        saveTransaction(userId, credits, RewardTxType.EARN, source, description,
                RewardTxStatus.COMPLETED, null);
    }

    // ── Redeem (pending → approve/reject lifecycle) ───────────────────────────

    @Override
    @Transactional
    public void redeemCredits(Long userId, Long credits, String description, String referenceId) {
        validateCredits(credits);
        UserReward reward = getLockedReward(userId);

        long available = reward.getTotalCredits()
                - reward.getRedeemedCredits()
                - reward.getPendingCredits();
        if (available < credits) {
            throw new BadRequestException("Insufficient credits. Available: " + available);
        }

        reward.setPendingCredits(reward.getPendingCredits() + credits);
        rewardRepo.save(reward);

        saveTransaction(userId, credits, RewardTxType.REDEEM, RewardSource.BONUS,
                description, RewardTxStatus.PENDING, referenceId);
    }

    @Override
    @Transactional
    public void approveRedeem(String referenceId) {
        RewardTransaction tx = txRepo.findByReferenceId(referenceId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found"));
        if (tx.getStatus() != RewardTxStatus.PENDING)
            throw new BadRequestException("Already processed");

        UserReward reward = getLockedReward(tx.getUser().getId());
        reward.setPendingCredits(reward.getPendingCredits() - tx.getCredits());
        reward.setRedeemedCredits(reward.getRedeemedCredits() + tx.getCredits());
        rewardRepo.save(reward);

        tx.setStatus(RewardTxStatus.COMPLETED);
        txRepo.save(tx);
    }

    @Override
    @Transactional
    public void rejectRedeem(String referenceId) {
        RewardTransaction tx = txRepo.findByReferenceId(referenceId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found"));
        if (tx.getStatus() != RewardTxStatus.PENDING)
            throw new BadRequestException("Already processed");

        UserReward reward = getLockedReward(tx.getUser().getId());
        reward.setPendingCredits(reward.getPendingCredits() - tx.getCredits());
        rewardRepo.save(reward);

        tx.setStatus(RewardTxStatus.FAILED);
        txRepo.save(tx);
    }

    // ── Admin credit / debit ──────────────────────────────────────────────────

    @Override
    @Transactional
    public void adminCredit(Long userId, Long credits, String reason) {
        if (credits == null || credits <= 0)
            throw new BadRequestException("Credits must be positive");

        UserReward reward = getLockedReward(userId);
        reward.setTotalCredits(reward.getTotalCredits() + credits);
        rewardRepo.save(reward);

        saveTransaction(userId, credits, RewardTxType.EARN, RewardSource.ADMIN,
                reason, RewardTxStatus.COMPLETED, null);
        logAdminAction("MANUAL_CREDIT", userId, credits, reason);
    }

    @Override
    @Transactional
    public void adminDebit(Long userId, Long credits, String reason) {
        if (credits == null || credits <= 0)
            throw new BadRequestException("Credits must be positive");

        UserReward reward = getLockedReward(userId);
        long available = reward.getTotalCredits()
                - reward.getRedeemedCredits()
                - reward.getPendingCredits();
        if (available < credits)
            throw new BadRequestException("Cannot debit " + credits + " credits. Available: " + available);

        reward.setTotalCredits(reward.getTotalCredits() - credits);
        rewardRepo.save(reward);

        saveTransaction(userId, credits, RewardTxType.REDEEM, RewardSource.ADMIN,
                reason, RewardTxStatus.COMPLETED, null);
        logAdminAction("MANUAL_DEBIT", userId, credits, reason);
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public UserReward getBalance(Long userId) {
        return rewardRepo.findByUserId(userId).orElseGet(() -> {
            UserReward empty = new UserReward();
            empty.setTotalCredits(0L);
            empty.setRedeemedCredits(0L);
            empty.setPendingCredits(0L);
            return empty;
        });
    }

    /**
     * FIX: Returns PageResponse<RewardTransactionDTO> — the DTO is built
     * INSIDE this @Transactional method while the Hibernate session is open,
     * so getCredits(), getType(), getSource() etc. are all loaded from already-
     * fetched columns. We never call tx.getUser(), so the lazy proxy is never
     * touched and Jackson never sees a ByteBuddyInterceptor.
     */
    @Override
    @Transactional(readOnly = true)
    public PageResponse<RewardTransactionDTO> getTransactions(Long userId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<RewardTransaction> pg = txRepo.findByUser_Id(userId, pageable);

        // Map to DTO inside the transaction — session is still open here
        List<RewardTransactionDTO> dtos = pg.getContent()
                .stream()
                .map(RewardTransactionDTO::from)
                .collect(Collectors.toList());

        return PageResponse.<RewardTransactionDTO>builder()
                .content(dtos)
                .page(pg.getNumber())
                .size(pg.getSize())
                .totalElements(pg.getTotalElements())
                .totalPages(pg.getTotalPages())
                .last(pg.isLast())
                .build();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void logAdminAction(String action, Long targetId, Long amount, String reason) {
        String adminEmail = SecurityContextHolder.getContext().getAuthentication() != null
                ? SecurityContextHolder.getContext().getAuthentication().getName()
                : "SYSTEM";

        adminAuditRepo.save(AdminAuditLog.builder()
                .adminEmail(adminEmail)
                .actionType(action)
                .targetUserId(targetId)
                .amount(amount)
                .reason(reason)
                .build());
    }

    private void checkDailyEarnCap(Long userId, Long credits, Long maxDailyEarn) {
        ZoneOffset zone = ZoneOffset.of(timezoneOffset);
        OffsetDateTime midnight = OffsetDateTime.now(zone)
                .toLocalDate().atStartOfDay().atOffset(zone);

        Long earnedToday = txRepo.sumEarnedSince(userId, midnight);
        if (earnedToday == null) earnedToday = 0L;

        if (earnedToday + credits > maxDailyEarn) {
            throw new BadRequestException(
                    "Daily cap reached. Max: " + maxDailyEarn + ", earned: " + earnedToday);
        }
    }

    private UserReward getLockedReward(Long userId) {
        getOrCreate(userId);
        return rewardRepo.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Reward not found"));
    }

    private UserReward getOrCreate(Long userId) {
        return rewardRepo.findByUserId(userId).orElseGet(() -> {
            User user = userRepo.findById(userId)
                    .orElseThrow(() -> new ResourceNotFoundException("User not found"));
            return rewardRepo.save(UserReward.builder().user(user).build());
        });
    }

    private RewardConfig getActiveConfig() {
        return configRepo.findFirstByIsActiveTrue().orElseGet(RewardConfig::defaults);
    }

    private void validateCredits(Long credits) {
        if (credits == null || credits <= 0)
            throw new BadRequestException("Invalid credits");
    }

    private void saveTransaction(Long userId, Long credits, RewardTxType type,
                                  RewardSource source, String description,
                                  RewardTxStatus status, String referenceId) {
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        txRepo.save(RewardTransaction.builder()
                .user(user)
                .credits(credits)
                .type(type)
                .source(source)
                .description(description)
                .status(status)
                .referenceId(referenceId != null ? referenceId : UUID.randomUUID().toString())
                .build());
    }
}