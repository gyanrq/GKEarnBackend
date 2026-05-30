package com.myworld.modules.referral.infrastructure;

import com.myworld.modules.referral.domain.MilestoneState;
import com.myworld.modules.referral.domain.MilestoneStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MilestoneStatusRepository extends JpaRepository<MilestoneStatus, Long> {

    List<MilestoneStatus> findByUserId(Long userId);

    Optional<MilestoneStatus> findByUserIdAndMilestoneId(Long userId, Long milestoneId);

    List<MilestoneStatus> findByUserIdAndState(Long userId, MilestoneState state);

    /**
     * FIX: Pessimistic write lock (SELECT FOR UPDATE) used by MilestoneRewardEngine.
     * Prevents a race condition where two concurrent ReferralSuccessEvents both read
     * state=LOCKED before either transaction commits, causing the milestone reward
     * to be granted twice.
     *
     * The DB unique constraint on (user_id, milestone_id) in MilestoneStatus is the
     * second line of defence — it will throw a ConstraintViolationException if the
     * lock somehow fails (e.g. in a non-serializable isolation level).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT ms FROM MilestoneStatus ms WHERE ms.user.id = :userId AND ms.milestone.id = :milestoneId")
    Optional<MilestoneStatus> findByUserIdAndMilestoneIdForUpdate(
            @Param("userId")      Long userId,
            @Param("milestoneId") Long milestoneId);
}