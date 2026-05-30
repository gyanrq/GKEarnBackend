package com.myworld.modules.referral.infrastructure;

import com.myworld.modules.referral.domain.Referral;
import com.myworld.modules.referral.domain.ReferralStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ReferralRepository extends JpaRepository<Referral, Long> {

    List<Referral> findByReferrerId(Long referrerId);

    Page<Referral> findByReferrerIdOrderByCreatedAtDesc(Long referrerId, Pageable pageable);

    long countByReferrerIdAndStatus(Long referrerId, ReferralStatus status);

    long countByStatus(ReferralStatus status);

    boolean existsByReferrerIdAndReferredId(Long referrerId, Long referredId);

    List<Referral> findByReferredIdAndStatus(Long referredId, ReferralStatus status);

    @Query("SELECT COUNT(r) > 0 FROM Referral r " +
            "WHERE r.referred.id = :referredUserId AND r.bonusGiven = :bonusGiven")
     boolean existsByReferredUserIdAndBonusGiven(
             @Param("referredUserId") Long referredUserId,
             @Param("bonusGiven")     boolean bonusGiven);
}