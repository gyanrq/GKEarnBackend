package com.myworld.modules.payout.application;

import com.myworld.core.dto.PageResponse;
import com.myworld.modules.payout.api.PayoutDTO;
import com.myworld.modules.payout.domain.PaymentStatus;

public interface PayoutService {
    void requestPayout(Long userId, PayoutDTO dto, jakarta.servlet.http.HttpServletRequest httpRequest);
    void approvePayout(Long payoutId, String adminEmail);
    void rejectPayout(Long payoutId, String reason, String adminEmail);
    void markPaid(Long payoutId, String transactionRef, String adminEmail);
    PageResponse<PayoutDTO> getUserPayouts(Long userId, int page, int size);
    PageResponse<PayoutDTO> getAllPayouts(int page, int size);
    // FIX: accepts PaymentStatus enum — was accepting raw String (typo risk)
    PageResponse<PayoutDTO> getPayoutsByStatus(PaymentStatus status, int page, int size);
}