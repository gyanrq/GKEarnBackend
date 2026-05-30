package com.myworld.modules.tasks.infrastructure;

import com.myworld.modules.tasks.domain.TaskConfig;
import com.myworld.modules.tasks.domain.TaskType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TaskConfigRepository extends JpaRepository<TaskConfig, Long> {
    Optional<TaskConfig> findByTaskType(TaskType taskType);
    List<TaskConfig> findAllByOrderByTaskTypeAsc();
    List<TaskConfig> findByIsActiveTrue();
}