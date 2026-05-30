package com.myworld.modules.tasks.dto;

import lombok.*;

/**
 * Full task response sent to mobile app for GET /api/tasks/today
 */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TaskDTO {

    private String taskType;
    private String label;
    private String description;
    private Long   credits;
    private String category;   // DAILY | CRYPTO | DEMAT | APP | KYC
    private String icon;

    /** True = user has already completed / claimed this task today */
    private boolean completed;

    /**
     * True = this task requires lead capture (email+phone) before
     * the partner URL is shown. Used by frontend to show LeadForm.
     */
    private boolean requiresLead;

    /**
     * Only populated when the user has already submitted their lead
     * (requiresLead=true and lead already recorded). Null otherwise.
     */
    private String partnerUrl;

    /**
     * Whether the user has already submitted their lead for this task
     * (so the app can show "Go to Site" directly without re-asking).
     */
    private boolean leadSubmitted;
}