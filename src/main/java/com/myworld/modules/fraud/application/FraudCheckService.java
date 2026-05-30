package com.myworld.modules.fraud.application;

import com.myworld.modules.identity.domain.User;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * FraudCheckService — backward-compatible wrapper around RiskScoringService.
 * RedeemService still calls fraudCheckService.check() — no changes needed there.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FraudCheckService {

    private final RiskScoringService riskScoringService;

    public FraudResult check(User user, String paymentDetails, HttpServletRequest request) {
        RiskResult risk = riskScoringService.evaluate(user, paymentDetails, request);
        return new FraudResult(risk.isHardBlock(), risk.isHold(), risk.getFlags());
    }
}
