package com.myworld.modules.tasks.domain;

import com.myworld.core.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

/**
 * Admin-configurable task definition.
 *
 * LINK_* tasks carry a partnerUrl that is revealed to the user only after
 * they submit their lead (email + phone).  requiresLead = true triggers
 * the lead-capture flow in the mobile app before the URL is unlocked.
 */
@Entity
@Table(name = "task_config",
    uniqueConstraints = @UniqueConstraint(name = "uq_task_config_type", columnNames = "task_type")
)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TaskConfig extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "task_type", nullable = false, unique = true)
    private TaskType taskType;

    /** Display name shown to user */
    @Column(nullable = false)
    private String label;

    /** Short description / what user must do */
    @Column(length = 500)
    private String description;

    /** Credits awarded on completion */
    @Column(nullable = false)
    private Long credits;

    /**
     * Category for grouping in UI:
     *   DAILY | CRYPTO | DEMAT | APP | KYC
     */
    @Column(nullable = false)
    @Builder.Default
    private String category = "DAILY";

    /** Emoji or icon key used in UI */
    @Column(length = 10)
    private String icon;

    /**
     * Partner URL unlocked after lead capture.
     * Null for non-link tasks.
     */
    @Column(length = 1000)
    private String partnerUrl;

    /**
     * If true, the user must submit email + phone BEFORE
     * the partnerUrl is revealed.
     */
    @Column(nullable = false)
    @Builder.Default
    private Boolean requiresLead = false;

    /** Whether this task is shown to users */
    @Builder.Default
    @Column(nullable = false)
    private Boolean isActive = true;

    /** Display order within category */
    @Builder.Default
    @Column(nullable = false)
    private Integer sortOrder = 0;
}