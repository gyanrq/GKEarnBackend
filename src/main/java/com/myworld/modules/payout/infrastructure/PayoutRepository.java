package com.myworld.modules.payout.infrastructure;

import com.myworld.modules.payout.domain.PaymentStatus;
import com.myworld.modules.payout.domain.PayoutRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;

public interface PayoutRepository extends JpaRepository<PayoutRequest, Long> {

    Page<PayoutRequest> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
    Page<PayoutRequest> findByStatusOrderByCreatedAtAsc(PaymentStatus status, Pageable pageable);
    Page<PayoutRequest> findAllByOrderByCreatedAtDesc(Pageable pageable);

    long countByStatus(PaymentStatus status);
    
    // FIX: For checking idempotency
    boolean existsByIdempotencyKey(String idempotencyKey);

    // FIX: Look up a payout by Razorpay reference (used in webhook handleFailed)
    java.util.Optional<PayoutRequest> findByTransactionRef(String transactionRef);

    long countByUserIdAndStatusAndCreatedAtAfter(Long userId, PaymentStatus status, java.time.OffsetDateTime date);

    @Query("SELECT SUM(p.amount) FROM PayoutRequest p WHERE p.user.id = :userId AND p.createdAt > :date AND p.status != 'REJECTED' AND p.status != 'FAILED'")
    BigDecimal sumAmountByUserIdAndCreatedAtAfter(@Param("userId") Long userId, @Param("date") java.time.OffsetDateTime date);

    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM PayoutRequest p WHERE p.status = :status")
    BigDecimal sumAmountByStatus(@Param("status") PaymentStatus status);
}