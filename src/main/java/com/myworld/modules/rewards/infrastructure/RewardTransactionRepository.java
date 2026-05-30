package com.myworld.modules.rewards.infrastructure;

import com.myworld.modules.rewards.domain.RewardSource;
import com.myworld.modules.rewards.domain.RewardTransaction;
import com.myworld.modules.rewards.domain.RewardTxStatus;
import com.myworld.modules.rewards.domain.RewardTxType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface RewardTransactionRepository extends JpaRepository<RewardTransaction, Long> {

    // ── Per-user analytics ─────────────────────────────────────────────────────
    @Query("SELECT COALESCE(SUM(t.credits), 0) FROM RewardTransaction t " +
           "WHERE t.user.id = :userId " +
           "AND t.type = com.myworld.modules.rewards.domain.RewardTxType.EARN " +
           "AND t.createdAt >= :since")
    Long sumEarnedSince(@Param("userId") Long userId, @Param("since") OffsetDateTime since);

    @Query("SELECT COUNT(t) FROM RewardTransaction t " +
           "WHERE t.user.id = :userId " +
           "AND t.type = com.myworld.modules.rewards.domain.RewardTxType.EARN " +
           "AND t.createdAt >= :since")
    long countEarnTransactionsSince(@Param("userId") Long userId, @Param("since") OffsetDateTime since);

    @Query("SELECT COUNT(t) FROM RewardTransaction t " +
           "WHERE t.user.id = :userId " +
           "AND t.type = com.myworld.modules.rewards.domain.RewardTxType.EARN")
    long countEarnTransactionsByUser(@Param("userId") Long userId);

    // ── Admin dashboard ────────────────────────────────────────────────────────
    long countByTypeAndStatus(RewardTxType type, RewardTxStatus status);

    Page<RewardTransaction> findByUser_Id(Long userId, Pageable pageable);

    Optional<RewardTransaction> findByReferenceId(String referenceId);

    // ── Earnings by Source (NEW — pie chart data) ──────────────────────────────
    /**
     * Returns one row per RewardSource: [source (String), totalCredits (Long)].
     * Only counts EARN transactions so REDEEM/DEBIT don't inflate the numbers.
     */
    @Query("SELECT t.source, COALESCE(SUM(t.credits), 0) " +
           "FROM RewardTransaction t " +
           "WHERE t.type = com.myworld.modules.rewards.domain.RewardTxType.EARN " +
           "GROUP BY t.source")
    List<Object[]> sumCreditsBySource();

    // ── Burn-rate analytics (NEW) ──────────────────────────────────────────────
    /**
     * Total EARN credits in the given window — used to compute avgDailyIssued.
     */
    @Query("SELECT COALESCE(SUM(t.credits), 0) FROM RewardTransaction t " +
           "WHERE t.type = com.myworld.modules.rewards.domain.RewardTxType.EARN " +
           "AND t.createdAt >= :since")
    long sumEarnCreditsInWindow(@Param("since") OffsetDateTime since);

    /**
     * Total REDEEM credits in the given window — used to compute avgDailyRedeemed.
     */
    @Query("SELECT COALESCE(SUM(t.credits), 0) FROM RewardTransaction t " +
           "WHERE t.type = com.myworld.modules.rewards.domain.RewardTxType.REDEEM " +
           "AND t.createdAt >= :since")
    long sumRedeemCreditsInWindow(@Param("since") OffsetDateTime since);
}