package com.myworld.modules.fraud.application;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

/**
 * FraudResult — immutable result from FraudCheckService.check().
 *
 * hardBlock = true  → block redemption immediately, credits NOT deducted
 * hold      = true  → queue for admin review, credits NOT deducted
 * isClear()         → proceed with normal auto-payout
 */
@Getter
@AllArgsConstructor
public class FraudResult {
    private final boolean      hardBlock;
    private final boolean      hold;
    private final List<String> flags;

    public boolean isClear() {
        return !hardBlock && !hold;
    }
}
