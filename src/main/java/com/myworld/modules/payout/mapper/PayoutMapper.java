package com.myworld.modules.payout.mapper;

import com.myworld.modules.payout.api.PayoutDTO;
import com.myworld.modules.payout.domain.PayoutRequest;

public class PayoutMapper {

    private PayoutMapper() {}

    public static PayoutDTO toDTO(PayoutRequest r) {
        return PayoutDTO.builder()
                .id(r.getId())
                .userId(r.getUser()  != null ? r.getUser().getId()    : null)
                .userName(r.getUser()  != null ? r.getUser().getName() : null)
                .userEmail(r.getUser() != null ? r.getUser().getEmail() : null)
                .amount(r.getAmount())
                .payoutType(r.getPayoutType())
                .payoutDetails(r.getPaymentDetails())
                // FIX: PayoutRequest.status is now a PaymentStatus enum — serialize as string name
                .status(r.getStatus() != null ? r.getStatus().name() : null)
                .adminNotes(r.getAdminNotes())
                .transactionRef(r.getTransactionRef())
                .createdAt(r.getCreatedAt())
                .updatedAt(r.getUpdatedAt())
                .build();
    }
}