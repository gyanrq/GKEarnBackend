package com.myworld.modules.admin.api;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TaskRewardSummaryDTO {
    private String taskType;
    private long   totalUsersCompleted;
    private long   totalCreditsIssued;
}