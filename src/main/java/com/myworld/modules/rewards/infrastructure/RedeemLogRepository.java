package com.myworld.modules.rewards.infrastructure;

import com.myworld.modules.rewards.domain.RedeemLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

@Repository
public interface RedeemLogRepository extends JpaRepository<RedeemLog, Long> {

    @Query("SELECT COUNT(r) FROM RedeemLog r " +
           "WHERE r.userId = :userId " +
           "AND r.status IN ('INITIATED', 'COMPLETED', 'UNDER_REVIEW') " +
           "AND r.createdAt >= :since")
    long countByUserIdAndCreatedAtAfter(
            @Param("userId") Long userId,
            @Param("since")  OffsetDateTime since);

    @Query("SELECT COALESCE(SUM(r.rupeeValue), 0) FROM RedeemLog r " +
           "WHERE r.userId = :userId " +
           "AND r.status IN ('INITIATED', 'COMPLETED') " +
           "AND r.createdAt >= :since")
    BigDecimal sumSuccessfulAmountByUserIdAndCreatedAtAfter(
            @Param("userId") Long userId,
            @Param("since")  OffsetDateTime since);

    boolean existsByReferenceId(String referenceId);

    List<RedeemLog> findByStatusOrderByCreatedAtAsc(String status);

    @Query("UPDATE RedeemLog r SET r.status = :status WHERE r.referenceId = :referenceId")
    @org.springframework.data.jpa.repository.Modifying
    void updateStatusByReferenceId(
            @Param("referenceId") String referenceId,
            @Param("status")      String status);

    // ── Fraud: count other users sharing same payment details ──────────────────
    @Query("SELECT COUNT(DISTINCT r.userId) FROM RedeemLog r " +
           "WHERE LOWER(r.paymentDetails) = LOWER(:paymentDetails) " +
           "AND r.userId != :excludeUserId")
    long countOtherUsersWithSamePaymentDetails(
            @Param("paymentDetails") String paymentDetails,
            @Param("excludeUserId")  Long   excludeUserId);

    // ── Admin duplicate panel: UPI groups with multiple users ──────────────────
    @Query("SELECT r.paymentDetails, COUNT(DISTINCT r.userId) AS cnt FROM RedeemLog r " +
           "GROUP BY r.paymentDetails HAVING COUNT(DISTINCT r.userId) > 1 " +
           "ORDER BY cnt DESC")
    List<Object[]> findSharedUpiGroups();

    // ── Get all userIds who used a specific UPI ────────────────────────────────
    @Query("SELECT DISTINCT r.userId FROM RedeemLog r " +
           "WHERE LOWER(r.paymentDetails) = LOWER(:paymentDetails)")
    List<Long> findUsersByPaymentDetails(@Param("paymentDetails") String paymentDetails);

    // ── Risk score audit: withdraw count in window ─────────────────────────────
    @Query("SELECT COUNT(r) FROM RedeemLog r " +
           "WHERE r.userId = :userId AND r.createdAt >= :since " +
           "AND r.status IN ('INITIATED','COMPLETED','UNDER_REVIEW')")
    long countWithdrawsSince(@Param("userId") Long userId, @Param("since") OffsetDateTime since);
}