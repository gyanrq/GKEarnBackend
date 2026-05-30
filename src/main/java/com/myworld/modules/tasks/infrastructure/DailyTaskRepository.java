package com.myworld.modules.tasks.infrastructure;

import com.myworld.modules.tasks.domain.DailyTaskCompletion;
import com.myworld.modules.tasks.domain.TaskType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;

@Repository
public interface DailyTaskRepository extends JpaRepository<DailyTaskCompletion, Long> {

    @Query("SELECT t FROM DailyTaskCompletion t " +
           "WHERE t.user.id = :userId AND t.createdAt >= :since")
    List<DailyTaskCompletion> findTodayCompletions(
            @Param("userId") Long userId,
            @Param("since")  OffsetDateTime since);

    boolean existsByUserIdAndTaskTypeAndCreatedAtAfter(
            Long userId, TaskType taskType, OffsetDateTime since);

    // ── Task Economy Report (NEW) ──────────────────────────────────────────────
    /**
     * One row per TaskType: [taskType (String), distinctUsers (Long), totalCredits (Long)].
     */
    @Query("SELECT t.taskType, COUNT(DISTINCT t.user.id), COALESCE(SUM(t.creditsEarned), 0) " +
           "FROM DailyTaskCompletion t " +
           "GROUP BY t.taskType")
    List<Object[]> summariseByTaskType();
}