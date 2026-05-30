package com.myworld.modules.referral.application;

import com.myworld.modules.identity.domain.User;
import com.myworld.modules.referral.api.ReferralSuccessEvent;
import com.myworld.modules.referral.domain.Referral;
import com.myworld.modules.referral.domain.ReferralStatus;
import com.myworld.modules.referral.infrastructure.ReferralRepository;
import com.myworld.modules.rewards.application.RewardService;
import com.myworld.modules.rewards.domain.RewardSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for EventProcessorServiceImpl.
 *
 * This covers the MOST CRITICAL fix in the entire codebase:
 *   verifyEmail() → processKycVerified() → earnCredits() chain.
 * Previously referrers never received reward credits because earnCredits()
 * was never called — these tests ensure it can never silently regress.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("EventProcessorServiceImpl — Referral reward chain")
class EventProcessorServiceImplTest {

    @Mock private ReferralRepository       referralRepo;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private RewardService             rewardService;

    @InjectMocks
    private EventProcessorServiceImpl service;

    private User referrer;
    private User referred;

    @BeforeEach
    void setUp() {
        // Inject @Value fields (no Spring context in unit test)
        ReflectionTestUtils.setField(service, "referrerBonusCredits",  100L);
        ReflectionTestUtils.setField(service, "referredBonusCredits",   50L);

        referrer = User.builder()
                .email("referrer@earnx.com").name("Referrer").password("x").build();
        ReflectionTestUtils.setField(referrer, "id", 1L);

        referred = User.builder()
                .email("referred@earnx.com").name("New User").password("x").build();
        ReflectionTestUtils.setField(referred, "id", 2L);
    }

    // =========================================================================
    //  processKycVerified — happy path
    // =========================================================================

    @Nested
    @DisplayName("processKycVerified() — happy path")
    class HappyPath {

        @Test
        @DisplayName("credits referrer with referrerBonusCredits from config")
        void creditReferrer_correctAmount() {
            Referral pending = buildPendingReferral(false);
            when(referralRepo.findByReferredIdAndStatus(2L, ReferralStatus.PENDING))
                    .thenReturn(List.of(pending));
            when(referralRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.processKycVerified(2L);

            verify(rewardService).earnCredits(
                    eq(1L),          // referrer ID
                    eq(100L),        // referrerBonusCredits
                    contains("Referral Bonus"),
                    eq(RewardSource.REFERRAL)
            );
        }

        @Test
        @DisplayName("credits referred user with referredBonusCredits from config")
        void creditReferredUser_correctAmount() {
            Referral pending = buildPendingReferral(false);
            when(referralRepo.findByReferredIdAndStatus(2L, ReferralStatus.PENDING))
                    .thenReturn(List.of(pending));
            when(referralRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.processKycVerified(2L);

            verify(rewardService).earnCredits(
                    eq(2L),          // referred user ID
                    eq(50L),         // referredBonusCredits
                    contains("Welcome Bonus"),
                    eq(RewardSource.REFERRAL)
            );
        }

        @Test
        @DisplayName("sets referral status to SUCCESS")
        void setsStatusSuccess() {
            Referral pending = buildPendingReferral(false);
            when(referralRepo.findByReferredIdAndStatus(2L, ReferralStatus.PENDING))
                    .thenReturn(List.of(pending));
            when(referralRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.processKycVerified(2L);

            assertThat(pending.getStatus()).isEqualTo(ReferralStatus.SUCCESS);
        }

        @Test
        @DisplayName("sets bonusGiven = true to prevent double-credit")
        void setsBonusGiven() {
            Referral pending = buildPendingReferral(false);
            when(referralRepo.findByReferredIdAndStatus(2L, ReferralStatus.PENDING))
                    .thenReturn(List.of(pending));
            when(referralRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.processKycVerified(2L);

            assertThat(pending.getBonusGiven()).isTrue();
        }

        @Test
        @DisplayName("publishes ReferralSuccessEvent for milestone engine")
        void publishesReferralSuccessEvent() {
            Referral pending = buildPendingReferral(false);
            when(referralRepo.findByReferredIdAndStatus(2L, ReferralStatus.PENDING))
                    .thenReturn(List.of(pending));
            when(referralRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.processKycVerified(2L);

            verify(eventPublisher).publishEvent(any(ReferralSuccessEvent.class));
        }

        @Test
        @DisplayName("earnCredits called BEFORE publishEvent — referrer credited first")
        void earnCreditsBeforePublish() {
            Referral pending = buildPendingReferral(false);
            when(referralRepo.findByReferredIdAndStatus(2L, ReferralStatus.PENDING))
                    .thenReturn(List.of(pending));
            when(referralRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            InOrder inOrder = inOrder(rewardService, eventPublisher);

            service.processKycVerified(2L);

            inOrder.verify(rewardService, atLeastOnce()).earnCredits(anyLong(), anyLong(), any(), any());
            inOrder.verify(eventPublisher).publishEvent(any());
        }

        @Test
        @DisplayName("handles multiple pending referrals for same referred user")
        void multipleReferrals_allProcessed() {
            Referral r1 = buildPendingReferral(false);
            Referral r2 = buildPendingReferral(false);
            when(referralRepo.findByReferredIdAndStatus(2L, ReferralStatus.PENDING))
                    .thenReturn(List.of(r1, r2));
            when(referralRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.processKycVerified(2L);

            // 2 referrals × 2 earnCredits calls each = 4 total
            verify(rewardService, times(4)).earnCredits(anyLong(), anyLong(), any(), any());
            verify(eventPublisher, times(2)).publishEvent(any(ReferralSuccessEvent.class));
        }
    }

    // =========================================================================
    //  processKycVerified — no referral case
    // =========================================================================

    @Nested
    @DisplayName("processKycVerified() — no referral")
    class NoReferral {

        @Test
        @DisplayName("no pending referrals — no credits awarded, no events")
        void noPendingReferrals_noOp() {
            when(referralRepo.findByReferredIdAndStatus(2L, ReferralStatus.PENDING))
                    .thenReturn(List.of());

            service.processKycVerified(2L);

            verify(rewardService, never()).earnCredits(anyLong(), anyLong(), any(), any());
            verify(eventPublisher, never()).publishEvent(any());
            verify(referralRepo, never()).save(any());
        }
    }

    // =========================================================================
    //  processKycVerified — double-credit prevention (bonusGiven guard)
    // =========================================================================

    @Nested
    @DisplayName("processKycVerified() — double-credit prevention")
    class DoubleCreditPrevention {

        @Test
        @DisplayName("bonusGiven=true → earnCredits NOT called (idempotent)")
        void bonusAlreadyGiven_skipsEarnCredits() {
            Referral alreadyBonused = buildPendingReferral(true); // bonusGiven = true
            when(referralRepo.findByReferredIdAndStatus(2L, ReferralStatus.PENDING))
                    .thenReturn(List.of(alreadyBonused));
            when(referralRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.processKycVerified(2L);

            // Status set to SUCCESS and event published, but no credits
            assertThat(alreadyBonused.getStatus()).isEqualTo(ReferralStatus.SUCCESS);
            verify(rewardService, never()).earnCredits(anyLong(), anyLong(), any(), any());
            verify(eventPublisher).publishEvent(any(ReferralSuccessEvent.class));
        }

        @Test
        @DisplayName("calling processKycVerified twice does not double-credit")
        void calledTwice_doesNotDoubleCredit() {
            Referral pending = buildPendingReferral(false);
            when(referralRepo.findByReferredIdAndStatus(2L, ReferralStatus.PENDING))
                    .thenReturn(List.of(pending))   // first call sees PENDING
                    .thenReturn(List.of());          // second call sees none (already SUCCESS)

            when(referralRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.processKycVerified(2L);
            service.processKycVerified(2L);

            // earnCredits called exactly twice: referrer + referred on first call only
            verify(rewardService, times(2)).earnCredits(anyLong(), anyLong(), any(), any());
        }
    }

    // =========================================================================
    //  configurable bonus amounts
    // =========================================================================

    @Nested
    @DisplayName("Configurable bonus amounts")
    class ConfigurableBonuses {

        @Test
        @DisplayName("uses referrerBonusCredits from @Value config (not hardcoded)")
        void usesConfiguredReferrerBonus() {
            // Change to non-default value to prove config is used
            ReflectionTestUtils.setField(service, "referrerBonusCredits", 250L);

            Referral pending = buildPendingReferral(false);
            when(referralRepo.findByReferredIdAndStatus(2L, ReferralStatus.PENDING))
                    .thenReturn(List.of(pending));
            when(referralRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.processKycVerified(2L);

            verify(rewardService).earnCredits(eq(1L), eq(250L), any(), any());
        }

        @Test
        @DisplayName("uses referredBonusCredits from @Value config (not hardcoded)")
        void usesConfiguredReferredBonus() {
            ReflectionTestUtils.setField(service, "referredBonusCredits", 75L);

            Referral pending = buildPendingReferral(false);
            when(referralRepo.findByReferredIdAndStatus(2L, ReferralStatus.PENDING))
                    .thenReturn(List.of(pending));
            when(referralRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.processKycVerified(2L);

            verify(rewardService).earnCredits(eq(2L), eq(75L), any(), any());
        }
    }

    // =========================================================================
    //  Helper
    // =========================================================================

    private Referral buildPendingReferral(boolean bonusGiven) {
        Referral r = Referral.builder()
                .referrer(referrer)
                .referred(referred)
                .referralCode("EX-ABC123")
                .status(ReferralStatus.PENDING)
                .bonusGiven(bonusGiven)
                .build();
        ReflectionTestUtils.setField(r, "id", 99L);
        return r;
    }
}
