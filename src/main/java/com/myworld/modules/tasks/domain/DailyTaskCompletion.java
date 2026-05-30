package com.myworld.modules.tasks.domain;

import com.myworld.core.domain.BaseEntity;
import com.myworld.modules.identity.domain.User;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "daily_task_completions",
    indexes = {
        @Index(name = "idx_task_user_date", columnList = "user_id, task_type")
    }
)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class DailyTaskCompletion extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, name = "task_type")
    private TaskType taskType;

    @Column(nullable = false)
    private Long creditsEarned;
}