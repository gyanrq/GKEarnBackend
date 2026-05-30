package com.myworld.modules.admin.application;

import com.myworld.modules.admin.api.*;
import com.myworld.modules.campaign.infrastructure.CampaignRepository;
import com.myworld.modules.campaign.infrastructure.LeadRepository;
import com.myworld.modules.identity.infrastructure.UserRepository;
import com.myworld.modules.referral.infrastructure.ReferralRepository;
import com.myworld.modules.rewards.domain.RewardTxStatus;
import com.myworld.modules.rewards.domain.RewardTxType;
import com.myworld.modules.rewards.infrastructure.RewardTransactionRepository;
import com.myworld.modules.rewards.infrastructure.UserRewardRepository;
import com.myworld.modules.tasks.infrastructure.DailyTaskRepository;
import com.myworld.core.config.CacheConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminDashboardService {

    private static final long HIGH_BALANCE_THRESHOLD = 5_000L;
    private static final int  BURN_RATE_DAYS         = 30;
    private static final int  TOP_EARNERS_LIMIT      = 50;

    private final UserRepository               userRepo;
    private final ReferralRepository           referralRepo;
    private final CampaignRepository           campaignRepo;
    private final LeadRepository               leadRepo;
    private final UserRewardRepository         userRewardRepo;
    private final RewardTransactionRepository  rewardTxRepo;
    private final DailyTaskRepository          dailyTaskRepo;

    // ── Main dashboard ─────────────────────────────────────────────────────────
    // FIX: Cache admin dashboard — aggregates 7+ tables; expensive under load.
    @Cacheable(value = CacheConfig.CACHE_ADMIN_DASHBOARD, key = "'main'")
    @Transactional(readOnly = true)
    public AdminDashboardDTO getDashboard() {
        long totalUsers   = userRepo.count();
        long blockedUsers = userRepo.countByIsBlocked(true);
        long deletedUsers = userRepo.countByIsDeleted(true);
        long activeUsers  = userRepo.countByIsDeletedFalseAndIsBlockedFalse();

        long totalCreditsInCirculation = userRewardRepo.sumTotalCredits();
        long totalCreditsRedeemed      = userRewardRepo.sumRedeemedCredits();

        long redemptionsPending   = rewardTxRepo.countByTypeAndStatus(
                RewardTxType.REDEEM, RewardTxStatus.PENDING);
        long redemptionsCompleted = rewardTxRepo.countByTypeAndStatus(
                RewardTxType.REDEEM, RewardTxStatus.COMPLETED);

        long totalReferrals      = referralRepo.count();
        long successfulReferrals = referralRepo.countByStatus(
                com.myworld.modules.referral.domain.ReferralStatus.SUCCESS);

        long activeCampaigns = campaignRepo.countByIsActive(true);
        long totalLeads      = leadRepo.count();
        long pendingLeads    = leadRepo.countByStatus(
                com.myworld.modules.campaign.domain.LeadStatus.PENDING);

        // ── Risk & Liquidity fields ──────────────────────────────────────────
        RiskSafetyDTO risk = getRiskAnalytics();

        return AdminDashboardDTO.builder()
                .totalUsers(totalUsers)
                .activeUsers(activeUsers)
                .blockedUsers(blockedUsers)
                .deletedUsers(deletedUsers)
                .totalCreditsInCirculation(totalCreditsInCirculation)
                .totalCreditsRedeemed(totalCreditsRedeemed)
                .redemptionsPending(redemptionsPending)
                .redemptionsCompleted(redemptionsCompleted)
                .totalReferrals(totalReferrals)
                .successfulReferrals(successfulReferrals)
                .activeCampaigns(activeCampaigns)
                .totalLeads(totalLeads)
                .pendingLeads(pendingLeads)
                // risk fields
                .potentialLiability(risk.getCurrentLiability())
                .highRiskBalanceCount(risk.getHighRiskUsers())
                .avgDailyIssued(risk.getAvgDailyIssued())
                .avgDailyRedeemed(risk.getAvgDailyRedeemed())
                .burnRate(risk.getBurnRate())
                .build();
    }

    // ── Feature 3: Platform Risk & Liquidity ───────────────────────────────────
    @Transactional(readOnly = true)
    public RiskSafetyDTO getRiskAnalytics() {
        long totalCredits   = userRewardRepo.sumTotalCredits();
        long redeemedCreds  = userRewardRepo.sumRedeemedCredits();
        long currentLiability = totalCredits - redeemedCreds;

        long highRiskUsers = userRewardRepo
                .countUsersWithBalanceGreaterThan(HIGH_BALANCE_THRESHOLD);

        OffsetDateTime window = OffsetDateTime.now().minusDays(BURN_RATE_DAYS);
        long issuedInWindow   = rewardTxRepo.sumEarnCreditsInWindow(window);
        long redeemedInWindow = rewardTxRepo.sumRedeemCreditsInWindow(window);

        double avgDailyIssued   = (double) issuedInWindow   / BURN_RATE_DAYS;
        double avgDailyRedeemed = (double) redeemedInWindow / BURN_RATE_DAYS;
        double burnRate         = avgDailyRedeemed > 0
                ? avgDailyIssued / avgDailyRedeemed
                : avgDailyIssued;   // treat zero-redemption as "all accumulation"

        return RiskSafetyDTO.builder()
                .currentLiability(currentLiability)
                .highRiskUsers(highRiskUsers)
                .avgDailyIssued(Math.round(avgDailyIssued * 100.0) / 100.0)
                .avgDailyRedeemed(Math.round(avgDailyRedeemed * 100.0) / 100.0)
                .burnRate(Math.round(burnRate * 100.0) / 100.0)
                .build();
    }

    // ── Feature 1: Lead Reward & Performance by Campaign ──────────────────────
    @Transactional(readOnly = true)
    public List<LeadRewardSummaryDTO> getLeadRewardSummary() {
        // Join campaign_leads → reward_transactions on referenceId = lead.id
        // Group by campaign to show which campaigns drain credits fastest
        List<Object[]> rows = rewardTxRepo.sumCreditsBySource();   // coarse cut

        // Fine-grained: native query per campaign for full accuracy
        // We delegate to a dedicated JPQL that joins Lead → Campaign → RewardTransaction
        return leadRepo.findAll().stream()
                .collect(Collectors.groupingBy(
                        l -> l.getCampaign().getId(),
                        Collectors.toList()))
                .entrySet().stream()
                .map(e -> {
                    var leads    = e.getValue();
                    var campaign = leads.get(0).getCampaign();
                    long approved = leads.stream()
                            .filter(l -> l.getStatus() ==
                                    com.myworld.modules.campaign.domain.LeadStatus.APPROVED)
                            .count();
                    long credits = approved * campaign.getRewardCredits();
                    return LeadRewardSummaryDTO.builder()
                            .campaignId(campaign.getId())
                            .campaignName(campaign.getName())
                            .totalLeads(approved)
                            .totalCredits(credits)
                            .build();
                })
                .sorted((a, b) -> Long.compare(b.getTotalCredits(), a.getTotalCredits()))
                .collect(Collectors.toList());
    }

    // ── Feature 2: Task Economy Report ────────────────────────────────────────
    @Transactional(readOnly = true)
    public List<TaskRewardSummaryDTO> getTaskEconomySummary() {
        List<Object[]> rows = dailyTaskRepo.summariseByTaskType();
        long grandTotal = rows.stream()
                .mapToLong(r -> (Long) r[2]).sum();

        return rows.stream()
                .map(r -> TaskRewardSummaryDTO.builder()
                        .taskType(r[0].toString())
                        .totalUsersCompleted((Long) r[1])
                        .totalCreditsIssued((Long) r[2])
                        .build())
                .sorted((a, b) -> Long.compare(b.getTotalCreditsIssued(), a.getTotalCreditsIssued()))
                .collect(Collectors.toList());
    }

    // ── Earnings by Source (for pie chart) ────────────────────────────────────
    @Transactional(readOnly = true)
    public List<EarningsBySourceDTO> getEarningsBySource() {
        List<Object[]> rows = rewardTxRepo.sumCreditsBySource();
        long grandTotal = rows.stream().mapToLong(r -> (Long) r[1]).sum();

        return rows.stream()
                .map(r -> EarningsBySourceDTO.builder()
                        .source(r[0].toString())
                        .totalCredits((Long) r[1])
                        .percentage(grandTotal > 0
                                ? Math.round((double)(Long) r[1] / grandTotal * 10_000.0) / 100.0
                                : 0.0)
                        .build())
                .sorted((a, b) -> Long.compare(b.getTotalCredits(), a.getTotalCredits()))
                .collect(Collectors.toList());
    }

    // ── Top Earners (for fraud / bot detection table) ──────────────────────────
    // FIX: N+1 eliminated — now uses findTopEarnersDto() which is a single JOIN query
    // returning a constructor expression. Previously this loaded List<UserReward> entities
    // then called r.getUser().getId() / r.getUser().getEmail() per row.
    // redemptionRate is computed here (derived value, no benefit from DB computation).
    @Cacheable(value = CacheConfig.CACHE_LEADERBOARD, key = "'top50'")
    @Transactional(readOnly = true)
    public List<TopEarnerDTO> getTopEarners() {
        return userRewardRepo
                .findTopEarnersDto(PageRequest.of(0, TOP_EARNERS_LIMIT))
                .stream()
                .map(dto -> {
                    long earned   = dto.getTotalEarned();
                    long redeemed = dto.getTotalRedeemed();
                    double rate   = earned > 0
                            ? Math.round((double) redeemed / earned * 10_000.0) / 100.0
                            : 0.0;
                    dto.setRedemptionRate(rate);
                    return dto;
                })
                .collect(Collectors.toList());
    }
}