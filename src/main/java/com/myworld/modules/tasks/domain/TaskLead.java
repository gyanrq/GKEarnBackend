package com.myworld.modules.tasks.domain;

import com.myworld.core.domain.BaseEntity;
import com.myworld.modules.identity.domain.User;
import jakarta.persistence.*;
import lombok.*;

/**
 * Records that a user submitted their contact details for a
 * partner link task.  Once saved, the partnerUrl is unlocked.
 */
@Entity
@Table(name = "task_leads",
    indexes = {
        @Index(name = "idx_task_lead_user_task", columnList = "user_id, task_type")
    }
)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TaskLead extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "task_type", nullable = false)
    private TaskType taskType;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private String mobile;

    /** The partner URL that was revealed after lead capture */
    @Column(length = 1000)
    private String partnerUrl;
}