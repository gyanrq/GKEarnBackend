package com.myworld.modules.rewards.infrastructure;

import com.myworld.modules.admin.api.TopEarnerDTO;
import com.myworld.modules.rewards.domain.UserReward;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRewardRepository extends JpaRepository<UserReward, Long> {

    Optional<UserReward> findByUserId(Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM UserReward r WHERE r.user.id = :userId")
    Optional<UserReward> findByUserIdForUpdate(@Param("userId") Long userId);

    // ── Admin dashboard aggregates ─────────────────────────────────────────────
    @Query("SELECT COALESCE(SUM(r.totalCredits), 0) FROM UserReward r")
    long sumTotalCredits();

    @Query("SELECT COALESCE(SUM(r.redeemedCredits), 0) FROM UserReward r")
    long sumRedeemedCredits();

    // ── Risk & Liquidity (NEW) ─────────────────────────────────────────────────

    /**
     * Count users whose unredeemed balance (totalCredits - redeemedCredits)
     * exceeds the given threshold. Used to identify high-payout-risk accounts.
     */
    @Query("SELECT COUNT(r) FROM UserReward r " +
           "WHERE (r.totalCredits - r.redeemedCredits) > :threshold")
    long countUsersWithBalanceGreaterThan(@Param("threshold") long threshold);

    /**
     * FIX: N+1 elimination — single JOIN query returning TopEarnerDTO directly.
     *
     * Previously: findTopEarners() fetched List<UserReward> (with JOIN FETCH on user),
     * then AdminDashboardService.getTopEarners() streamed over them calling
     * r.getUser().getId() and r.getUser().getEmail() per row. Even with JOIN FETCH
     * the mapping overhead is unnecessary — a constructor expression returns exactly
     * what the DTO needs in one pass, no entity hydration required.
     *
     * netBalance and redemptionRate are computed in the service layer (they are
     * derived values that don't need DB computation).
     */
    @Query("""
        SELECT new com.myworld.modules.admin.api.TopEarnerDTO(
            u.id,
            u.email,
            r.totalCredits,
            r.redeemedCredits,
            (r.totalCredits - r.redeemedCredits),
            0.0
        )
        FROM UserReward r
        JOIN r.user u
        WHERE u.isDeleted = false
        ORDER BY r.totalCredits DESC
        """)
    List<TopEarnerDTO> findTopEarnersDto(org.springframework.data.domain.Pageable pageable);

    /**
     * Kept for backward compatibility — prefer findTopEarnersDto() for new code.
     */
    @Query("""
        SELECT r FROM UserReward r
        JOIN FETCH r.user u
        WHERE u.isDeleted = false
        ORDER BY r.totalCredits DESC
        """)
    List<UserReward> findTopEarners(org.springframework.data.domain.Pageable pageable);
}