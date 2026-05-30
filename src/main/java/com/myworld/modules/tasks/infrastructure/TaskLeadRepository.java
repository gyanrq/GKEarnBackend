package com.myworld.modules.tasks.infrastructure;

import com.myworld.modules.tasks.domain.TaskLead;
import com.myworld.modules.tasks.domain.TaskType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TaskLeadRepository extends JpaRepository<TaskLead, Long> {

    boolean existsByUserIdAndTaskType(Long userId, TaskType taskType);

    Optional<TaskLead> findByUserIdAndTaskType(Long userId, TaskType taskType);
}