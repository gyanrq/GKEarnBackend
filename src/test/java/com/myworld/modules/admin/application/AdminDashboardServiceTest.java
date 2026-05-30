package com.myworld.modules.admin.application;

import com.myworld.modules.admin.api.AdminDashboardDTO;
import com.myworld.modules.campaign.domain.LeadStatus;
import com.myworld.modules.campaign.infrastructure.CampaignRepository;
import com.myworld.modules.campaign.infrastructure.LeadRepository;
import com.myworld.modules.identity.infrastructure.UserRepository;
import com.myworld.modules.referral.domain.ReferralStatus;
import com.myworld.modules.referral.infrastructure.ReferralRepository;
import com.myworld.modules.rewards.domain.RewardTxStatus;
import com.myworld.modules.rewards.domain.RewardTxType;
import com.myworld.modules.rewards.infrastructure.RewardTransactionRepository;
import com.myworld.modules.rewards.infrastructure.UserRewardRepository;
import com.myworld.modules.tasks.infrastructure.DailyTaskRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminDashboardService — Unit Tests")
class AdminDashboardServiceTest {

    @Mock private UserRepository              userRepo;
    @Mock private ReferralRepository          referralRepo;
    @Mock private CampaignRepository          campaignRepo;
    @Mock private LeadRepository              leadRepo;
    @Mock private UserRewardRepository        userRewardRepo;
    @Mock private RewardTransactionRepository rewardTxRepo;
    @Mock private DailyTaskRepository         dailyTaskRepo;

    @InjectMocks
    private AdminDashboardService service;

    @BeforeEach
    void setUp() {
        when(userRepo.count()).thenReturn(100L);
        when(userRepo.countByIsBlocked(true)).thenReturn(5L);
        when(userRepo.countByIsDeleted(true)).thenReturn(3L);
        when(userRepo.countByIsDeletedFalseAndIsBlockedFalse()).thenReturn(92L);
        when(userRewardRepo.sumTotalCredits()).thenReturn(500_000L);
        when(userRewardRepo.sumRedeemedCredits()).thenReturn(100_000L);
        when(rewardTxRepo.countByTypeAndStatus(RewardTxType.REDEEM, RewardTxStatus.PENDING)).thenReturn(12L);
        when(rewardTxRepo.countByTypeAndStatus(RewardTxType.REDEEM, RewardTxStatus.COMPLETED)).thenReturn(88L);
        when(referralRepo.count()).thenReturn(200L);
        when(referralRepo.countByStatus(ReferralStatus.SUCCESS)).thenReturn(150L);
        when(campaignRepo.countByIsActive(true)).thenReturn(8L);
        when(leadRepo.count()).thenReturn(1000L);
        when(leadRepo.countByStatus(LeadStatus.PENDING)).thenReturn(50L);
        when(userRewardRepo.countUsersWithBalanceGreaterThan(anyLong())).thenReturn(20L);
        when(rewardTxRepo.sumEarnCreditsInWindow(any(OffsetDateTime.class))).thenReturn(50_000L);
        when(rewardTxRepo.sumRedeemCreditsInWindow(any(OffsetDateTime.class))).thenReturn(10_000L);
    }

    @Nested
    @DisplayName("getDashboard()")
    class GetDashboard {

        @Test
        @DisplayName("returns correct user counts")
        void correctUserCounts() {
            AdminDashboardDTO dto = service.getDashboard();

            assertThat(dto.getTotalUsers()).isEqualTo(100L);
            assertThat(dto.getActiveUsers()).isEqualTo(92L);
            assertThat(dto.getBlockedUsers()).isEqualTo(5L);
            assertThat(dto.getDeletedUsers()).isEqualTo(3L);
        }

        @Test
        @DisplayName("returns correct credit totals")
        void correctCreditTotals() {
            AdminDashboardDTO dto = service.getDashboard();

            assertThat(dto.getTotalCreditsInCirculation()).isEqualTo(500_000L);
            assertThat(dto.getTotalCreditsRedeemed()).isEqualTo(100_000L);
        }

        @Test
        @DisplayName("returns correct referral stats")
        void correctReferralStats() {
            AdminDashboardDTO dto = service.getDashboard();

            assertThat(dto.getTotalReferrals()).isEqualTo(200L);
            assertThat(dto.getSuccessfulReferrals()).isEqualTo(150L);
        }

        @Test
        @DisplayName("returns correct campaign and lead stats")
        void correctCampaignStats() {
            AdminDashboardDTO dto = service.getDashboard();

            assertThat(dto.getActiveCampaigns()).isEqualTo(8L);
            assertThat(dto.getTotalLeads()).isEqualTo(1000L);
            assertThat(dto.getPendingLeads()).isEqualTo(50L);
        }

        @Test
        @DisplayName("pending liability = totalCredits - redeemedCredits")
        void correctLiability() {
            AdminDashboardDTO dto = service.getDashboard();
            // 500_000 - 100_000 = 400_000
            assertThat(dto.getPotentialLiability()).isEqualTo(400_000L);
        }

        @Test
        @DisplayName("returns non-null DTO (no NPE on any field)")
        void noNullPointerException() {
            assertThatCode(() -> service.getDashboard()).doesNotThrowAnyException();
            AdminDashboardDTO dto = service.getDashboard();
            assertThat(dto).isNotNull();
        }
    }
}
