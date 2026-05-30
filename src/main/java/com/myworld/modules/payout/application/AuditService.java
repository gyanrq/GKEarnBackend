package com.myworld.modules.payout.application;

import com.myworld.modules.rewards.domain.RedeemLog;
import com.myworld.modules.rewards.infrastructure.RedeemLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * AuditService
 *
 * FIX: Lock wait timeout was caused by calling logRedeemAttempt() from inside
 * RedeemService which ran under SERIALIZABLE isolation. Even though AuditService
 * uses REQUIRES_NEW, the existsByReferenceId() read was attempting to acquire
 * locks on redeem_log rows already held by the outer SERIALIZABLE transaction,
 * causing a deadlock / lock wait timeout.
 *
 * SOLUTION:
 *  1. RedeemService.redeem() is now READ_COMMITTED (outer gate checks only).
 *     The actual credit mutation (rewardService.redeemCredits) remains SERIALIZABLE.
 *  2. AuditService methods use REQUIRES_NEW — they always run in a completely
 *     separate connection/transaction with no lock contention.
 *  3. Removed the existsByReferenceId() pre-check. Instead we attempt a direct
 *     insert and catch DataIntegrityViolationException (duplicate referenceId)
 *     silently — this is safe because referenceId has a UNIQUE constraint.
 *     This eliminates the extra SELECT that was causing the lock conflict.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private final RedeemLogRepository redeemLogRepo;

    // ── Generic audit log ─────────────────────────────────────────────────────
    public void log(String actor, String action, String entity, Long entityId, String details) {
        log.info("[AUDIT] actor={} action={} entity={} id={} details={}",
                actor, action, entity, entityId, details);
    }

    // ── Layer 6: Redeem-specific audit (short signature) ──────────────────────
    /**
     * Runs in its own transaction (REQUIRES_NEW) so it commits independently
     * of the caller's transaction — audit writes survive even on rollback.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logRedeemAttempt(Long userId, Long credits, BigDecimal rupeeValue,
                                  String referenceId, String status, String fraudFlags) {
        doInsert(userId, credits, rupeeValue, "", referenceId, status,
                 fraudFlags != null ? fraudFlags : "", "");
    }

    // ── Layer 6: Redeem-specific audit (full signature) ───────────────────────
    /**
     * Full version used by RedeemService — includes paymentDetails and clientIp.
     * REQUIRES_NEW ensures this always commits in its own DB transaction,
     * completely independent of whatever isolation the caller uses.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logRedeemAttempt(Long userId, Long credits, BigDecimal rupeeValue,
                                  String paymentDetails, String referenceId,
                                  String status, String fraudFlags, String clientIp) {
        doInsert(userId, credits, rupeeValue,
                 paymentDetails != null ? paymentDetails : "",
                 referenceId, status,
                 fraudFlags  != null ? fraudFlags  : "",
                 clientIp    != null ? clientIp    : "");
    }

    // ── Internal insert — never throws (audit must not break main flow) ───────
    private void doInsert(Long userId, Long credits, BigDecimal rupeeValue,
                          String paymentDetails, String referenceId,
                          String status, String fraudFlags, String clientIp) {
        try {
            RedeemLog entry = RedeemLog.builder()
                    .userId(userId)
                    .credits(credits)
                    .rupeeValue(rupeeValue != null ? rupeeValue : BigDecimal.ZERO)
                    .paymentDetails(paymentDetails)
                    .referenceId(referenceId)
                    .status(status)
                    .fraudFlags(fraudFlags)
                    .clientIp(clientIp)
                    .build();
            redeemLogRepo.save(entry);
        } catch (DataIntegrityViolationException ex) {
            // Duplicate referenceId — already logged, silently ignore
            log.debug("[AUDIT] Duplicate redeem log skipped for ref={}", referenceId);
        } catch (Exception ex) {
            // Audit failure must NEVER propagate to the caller
            log.error("[AUDIT] Failed to write redeem log for ref={}: {}", referenceId, ex.getMessage());
        }
    }
}