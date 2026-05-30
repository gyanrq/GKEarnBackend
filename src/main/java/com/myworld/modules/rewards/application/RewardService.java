package com.myworld.modules.rewards.application;

import com.myworld.core.dto.PageResponse;
import com.myworld.modules.rewards.domain.RewardSource;
import com.myworld.modules.rewards.domain.UserReward;
import com.myworld.modules.rewards.web.RewardTransactionDTO;

/**
 * File: src/main/java/com/myworld/modules/rewards/application/RewardService.java
 *
 * CHANGE: getTransactions now returns PageResponse<RewardTransactionDTO>
 * instead of PageResponse<RewardTransaction> so the lazy User proxy
 * is never passed to Jackson.
 */
public interface RewardService {
    void earnCredits(Long userId, Long credits, String description, RewardSource source);
    void redeemCredits(Long userId, Long credits, String description, String referenceId);
    void approveRedeem(String referenceId);
    void rejectRedeem(String referenceId);
    UserReward getBalance(Long userId);

    // FIX: return DTO (not raw entity) — avoids ByteBuddyInterceptor serialization crash
    PageResponse<RewardTransactionDTO> getTransactions(Long userId, int page, int size);

    void adminCredit(Long userId, Long credits, String reason);
    void adminDebit(Long userId, Long credits, String reason);
}