package com.myworld.modules.fraud.application;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

/**
 * RiskResult — result from RiskScoringService.evaluate()
 *
 * score < 200  → isClear()    → auto-approve
 * score 200-499 → isHold()   → manual review queue
 * score >= 500  → isBlock()  → hard block + alert
 */
@Getter
@AllArgsConstructor
public class RiskResult {
    private final int          score;
    private final boolean      hardBlock;
    private final boolean      hold;
    private final List<String> flags;

    public boolean isClear() {
        return !hardBlock && !hold;
    }

    public String getDecision() {
        if (hardBlock) return "BLOCK";
        if (hold)      return "HOLD";
        return "CLEAR";
    }
}
