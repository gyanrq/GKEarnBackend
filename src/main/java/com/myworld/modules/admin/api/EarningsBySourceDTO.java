package com.myworld.modules.admin.api;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class EarningsBySourceDTO {
    private String source;
    private long   totalCredits;
    private double percentage;
}