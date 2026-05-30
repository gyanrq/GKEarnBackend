package com.myworld.modules.admin.api;

import com.myworld.modules.tasks.domain.TaskType;
import jakarta.validation.constraints.*;
import lombok.*;
import java.util.List;

public final class AdminSettingsDTO {

    private AdminSettingsDTO() {}

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class RewardConfigRequest {

        @NotNull @Min(1) @Max(1000)
        private Integer creditsPerRupee;

        @NotNull @Min(1)
        private Long minRedeemCredits;

        @NotNull @Min(100)
        private Long maxDailyEarn;

        /**
         * Account age gate in SECONDS.
         * 0 = no restriction (new accounts can redeem freely).
         */
        @NotNull @Min(0)
        private Long redemptionWaitSeconds;

        /**
         * Informational only — payout processing time in SECONDS.
         */
        @NotNull @Min(0)
        private Long payoutProcessingSeconds;

        /**
         * Redeem time window (IST, 24-hour, "HH:mm:ss").
         * Null/blank means no restriction — open all day.
         */
        @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d:[0-5]\\d$",
                 message = "redeemWindowStart must be HH:mm:ss (e.g. 09:00:00)")
        private String redeemWindowStart;

        @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d:[0-5]\\d$",
                 message = "redeemWindowEnd must be HH:mm:ss (e.g. 21:00:00)")
        private String redeemWindowEnd;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class TaskConfigItem {
        @NotNull
        private TaskType taskType;
        @NotNull @Min(0)
        private Long credits;
        private String label;
        private Boolean isActive;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class TaskConfigRequest {
        @NotNull @Size(min = 1)
        private List<TaskConfigItem> tasks;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class SpinPrizeItem {
        private Long id;
        @NotNull @Min(1)
        private Long credits;
        @NotNull @Min(0) @Max(10000)
        private Integer weight;
        @NotBlank
        private String label;
        private String color;
        private Boolean isActive;
        private Integer sortOrder;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class SpinConfigRequest {
        @NotNull @Size(min = 1)
        private List<SpinPrizeItem> prizes;
    }
}