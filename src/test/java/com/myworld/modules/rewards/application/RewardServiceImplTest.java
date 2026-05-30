package com.myworld.modules.rewards.application;

import com.myworld.core.exception.BadRequestException;
import com.myworld.core.exception.ResourceNotFoundException;
import com.myworld.modules.admin.infrastructure.AdminAuditRepository;
import com.myworld.modules.identity.domain.User;
import com.myworld.modules.identity.infrastructure.UserRepository;
import com.myworld.modules.rewards.domain.*;
import com.myworld.modules.rewards.infrastructure.*;
import com.myworld.modules.rewards.web.RewardTransactionDTO;
import com.myworld.core.dto.PageResponse;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for RewardServiceImpl.
 *
 * Strategy:
 *  - All dependencies mocked with Mockito — no Spring context, no DB.
 *  - Every public method tested for happy path + all failure branches.
 *  - SecurityContextHolder stubbed for admin credit/debit audit log tests.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("RewardServiceImpl")
class RewardServiceImplTest {

    // ── Mocks ─────────────────────────────────────────────────────────────────

    @Mock private UserRewardRepository        rewardRepo;
    @Mock private RewardTransactionRepository txRepo;
    @Mock private RewardConfigRepository      configRepo;
    @Mock private UserRepository              userRepo;
    @Mock private AdminAuditRepository        adminAuditRepo;

    @InjectMocks
    private RewardServiceImpl service;

    // ── Shared fixtures ───────────────────────────────────────────────────────

    private User            testUser;
    private UserReward      testReward;
    private RewardConfig    defaultConfig;

    @BeforeEach
    void setUp() {
        // Inject @Value fields manually (no Spring context)
        ReflectionTestUtils.setField(service, "timezoneOffset", "+05:30");

        testUser = User.builder()
                .email("test@earnx.com")
                .name("Test User")
                .password("hashed")
                .build();
        ReflectionTestUtils.setField(testUser, "id", 1L);

        testReward = UserReward.builder()
                .user(testUser)
                .totalCredits(500L)
                .redeemedCredits(0L)
                .pendingCredits(0L)
                .build();
        ReflectionTestUtils.setField(testReward, "id", 1L);

        defaultConfig = RewardConfig.defaults(); // maxDailyEarn = 4000
    }

    // =========================================================================
    //  earnCredits
    // =========================================================================

    @Nested
    @DisplayName("earnCredits()")
    class EarnCredits {

        @Test
        @DisplayName("happy path — credits added to totalCredits and transaction saved")
        void happyPath() {
            // given
            stubFindUser();
            stubActiveConfig(defaultConfig);
            stubDailyEarned(0L);
            stubLockedReward();

            // when
            service.earnCredits(1L, 100L, "Daily spin", RewardSource.SPIN);

            // then
            ArgumentCaptor<UserReward> rewardCaptor = ArgumentCaptor.forClass(UserReward.class);
            verify(rewardRepo).save(rewardCaptor.capture());
            assertThat(rewardCaptor.getValue().getTotalCredits()).isEqualTo(600L);

            ArgumentCaptor<RewardTransaction> txCaptor = ArgumentCaptor.forClass(RewardTransaction.class);
            verify(txRepo).save(txCaptor.capture());
            RewardTransaction saved = txCaptor.getValue();
            assertThat(saved.getCredits()).isEqualTo(100L);
            assertThat(saved.getType()).isEqualTo(RewardTxType.EARN);
            assertThat(saved.getSource()).isEqualTo(RewardSource.SPIN);
            assertThat(saved.getStatus()).isEqualTo(RewardTxStatus.COMPLETED);
        }

        @Test
        @DisplayName("zero credits → BadRequestException")
        void zeroCredits_throws() {
            assertThatThrownBy(() -> service.earnCredits(1L, 0L, "test", RewardSource.SPIN))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Invalid credits");
        }

        @Test
        @DisplayName("negative credits → BadRequestException")
        void negativeCredits_throws() {
            assertThatThrownBy(() -> service.earnCredits(1L, -50L, "test", RewardSource.SPIN))
                    .isInstanceOf(BadRequestException.class);
        }

        @Test
        @DisplayName("daily cap exactly at limit → throws")
        void dailyCapExact_throws() {
            stubFindUser();
            stubActiveConfig(defaultConfig); // maxDailyEarn = 4000
            stubDailyEarned(4000L);          // already at cap

            assertThatThrownBy(() -> service.earnCredits(1L, 1L, "extra", RewardSource.BONUS))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Daily cap");
        }

        @Test
        @DisplayName("daily earn + new credits under cap → succeeds")
        void underDailyCap_succeeds() {
            stubFindUser();
            stubActiveConfig(defaultConfig); // maxDailyEarn = 4000
            stubDailyEarned(3900L);
            stubLockedReward();

            // 3900 + 100 == 4000 == maxDailyEarn, boundary: NOT over, so allowed
            service.earnCredits(1L, 100L, "final spin", RewardSource.SPIN);
            verify(rewardRepo).save(any());
        }

        @Test
        @DisplayName("null daily earned treated as 0")
        void nullDailyEarned_treatedAsZero() {
            stubFindUser();
            stubActiveConfig(defaultConfig);
            when(txRepo.sumEarnedSince(anyLong(), any())).thenReturn(null);
            stubLockedReward();

            service.earnCredits(1L, 50L, "test", RewardSource.REFERRAL);
            verify(rewardRepo).save(any());
        }

        @Test
        @DisplayName("no active config → falls back to RewardConfig.defaults()")
        void noActiveConfig_usesDefaults() {
            stubFindUser();
            when(configRepo.findFirstByIsActiveTrue()).thenReturn(Optional.empty());
            stubDailyEarned(0L);
            stubLockedReward();

            service.earnCredits(1L, 200L, "test", RewardSource.CAMPAIGN);
            verify(rewardRepo).save(any());
        }
    }

    // =========================================================================
    //  redeemCredits
    // =========================================================================

    @Nested
    @DisplayName("redeemCredits()")
    class RedeemCredits {

        @Test
        @DisplayName("happy path — pending credits incremented and tx saved as PENDING")
        void happyPath() {
            stubLockedReward(); // total=500, redeemed=0, pending=0 → available=500

            service.redeemCredits(1L, 200L, "Payout request", "REF-001");

            ArgumentCaptor<UserReward> rewardCaptor = ArgumentCaptor.forClass(UserReward.class);
            verify(rewardRepo).save(rewardCaptor.capture());
            assertThat(rewardCaptor.getValue().getPendingCredits()).isEqualTo(200L);

            ArgumentCaptor<RewardTransaction> txCaptor = ArgumentCaptor.forClass(RewardTransaction.class);
            verify(txRepo).save(txCaptor.capture());
            assertThat(txCaptor.getValue().getStatus()).isEqualTo(RewardTxStatus.PENDING);
            assertThat(txCaptor.getValue().getType()).isEqualTo(RewardTxType.REDEEM);
            assertThat(txCaptor.getValue().getReferenceId()).isEqualTo("REF-001");
        }

        @Test
        @DisplayName("insufficient balance — throws BadRequestException")
        void insufficientBalance_throws() {
            // available = total(500) - redeemed(0) - pending(0) = 500
            // requesting 600 → insufficient
            stubLockedReward();

            assertThatThrownBy(() -> service.redeemCredits(1L, 600L, "desc", "REF-002"))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Insufficient credits");
        }

        @Test
        @DisplayName("exactly available balance — succeeds (boundary test)")
        void exactBalance_succeeds() {
            stubLockedReward(); // available = 500
            service.redeemCredits(1L, 500L, "full redeem", "REF-003");
            verify(rewardRepo).save(any());
        }

        @Test
        @DisplayName("pending credits reduce available balance")
        void pendingReducesAvailable() {
            // total=500, pending=400 → available=100; request 200 → insufficient
            testReward.setPendingCredits(400L);
            stubLockedReward();

            assertThatThrownBy(() -> service.redeemCredits(1L, 200L, "desc", "REF-004"))
                    .isInstanceOf(BadRequestException.class);
        }

        @Test
        @DisplayName("zero credits → BadRequestException")
        void zeroCredits_throws() {
            assertThatThrownBy(() -> service.redeemCredits(1L, 0L, "desc", "REF"))
                    .isInstanceOf(BadRequestException.class);
        }
    }

    // =========================================================================
    //  approveRedeem
    // =========================================================================

    @Nested
    @DisplayName("approveRedeem()")
    class ApproveRedeem {

        @Test
        @DisplayName("happy path — pending → redeemed, tx COMPLETED")
        void happyPath() {
            RewardTransaction tx = buildPendingTx("REF-APPROVE", 200L);
            when(txRepo.findByReferenceId("REF-APPROVE")).thenReturn(Optional.of(tx));

            testReward.setPendingCredits(200L);
            stubLockedReward();

            service.approveRedeem("REF-APPROVE");

            ArgumentCaptor<UserReward> rewardCaptor = ArgumentCaptor.forClass(UserReward.class);
            verify(rewardRepo).save(rewardCaptor.capture());
            UserReward saved = rewardCaptor.getValue();
            assertThat(saved.getPendingCredits()).isZero();
            assertThat(saved.getRedeemedCredits()).isEqualTo(200L);

            assertThat(tx.getStatus()).isEqualTo(RewardTxStatus.COMPLETED);
        }

        @Test
        @DisplayName("reference ID not found → ResourceNotFoundException")
        void notFound_throws() {
            when(txRepo.findByReferenceId("MISSING")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.approveRedeem("MISSING"))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("already COMPLETED tx → BadRequestException (no double-approve)")
        void alreadyCompleted_throws() {
            RewardTransaction tx = buildPendingTx("REF-X", 100L);
            tx.setStatus(RewardTxStatus.COMPLETED);
            when(txRepo.findByReferenceId("REF-X")).thenReturn(Optional.of(tx));

            assertThatThrownBy(() -> service.approveRedeem("REF-X"))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Already processed");
        }

        @Test
        @DisplayName("FAILED tx → BadRequestException")
        void alreadyFailed_throws() {
            RewardTransaction tx = buildPendingTx("REF-Y", 100L);
            tx.setStatus(RewardTxStatus.FAILED);
            when(txRepo.findByReferenceId("REF-Y")).thenReturn(Optional.of(tx));

            assertThatThrownBy(() -> service.approveRedeem("REF-Y"))
                    .isInstanceOf(BadRequestException.class);
        }
    }

    // =========================================================================
    //  rejectRedeem
    // =========================================================================

    @Nested
    @DisplayName("rejectRedeem()")
    class RejectRedeem {

        @Test
        @DisplayName("happy path — pending credits released, tx FAILED")
        void happyPath() {
            RewardTransaction tx = buildPendingTx("REF-REJECT", 150L);
            when(txRepo.findByReferenceId("REF-REJECT")).thenReturn(Optional.of(tx));

            testReward.setPendingCredits(150L);
            stubLockedReward();

            service.rejectRedeem("REF-REJECT");

            ArgumentCaptor<UserReward> rewardCaptor = ArgumentCaptor.forClass(UserReward.class);
            verify(rewardRepo).save(rewardCaptor.capture());
            assertThat(rewardCaptor.getValue().getPendingCredits()).isZero();

            assertThat(tx.getStatus()).isEqualTo(RewardTxStatus.FAILED);
        }

        @Test
        @DisplayName("reference not found → ResourceNotFoundException")
        void notFound_throws() {
            when(txRepo.findByReferenceId("MISSING")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.rejectRedeem("MISSING"))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("double-reject → BadRequestException")
        void doubleReject_throws() {
            RewardTransaction tx = buildPendingTx("REF-Z", 100L);
            tx.setStatus(RewardTxStatus.FAILED);
            when(txRepo.findByReferenceId("REF-Z")).thenReturn(Optional.of(tx));

            assertThatThrownBy(() -> service.rejectRedeem("REF-Z"))
                    .isInstanceOf(BadRequestException.class);
        }
    }

    // =========================================================================
    //  getBalance
    // =========================================================================

    @Nested
    @DisplayName("getBalance()")
    class GetBalance {

        @Test
        @DisplayName("existing reward returned as-is")
        void existingReward_returned() {
            when(rewardRepo.findByUserId(1L)).thenReturn(Optional.of(testReward));

            UserReward result = service.getBalance(1L);

            assertThat(result.getTotalCredits()).isEqualTo(500L);
            assertThat(result.getRedeemedCredits()).isZero();
            assertThat(result.getPendingCredits()).isZero();
        }

        @Test
        @DisplayName("no reward row → returns zero-balance object (not null, not exception)")
        void noReward_returnsZero() {
            when(rewardRepo.findByUserId(99L)).thenReturn(Optional.empty());

            UserReward result = service.getBalance(99L);

            assertThat(result).isNotNull();
            assertThat(result.getTotalCredits()).isZero();
            assertThat(result.getRedeemedCredits()).isZero();
            assertThat(result.getPendingCredits()).isZero();
        }
    }

    // =========================================================================
    //  adminCredit
    // =========================================================================

    @Nested
    @DisplayName("adminCredit()")
    class AdminCredit {

        @BeforeEach
        void stubSecurityContext() {
            Authentication auth = mock(Authentication.class);
            when(auth.getName()).thenReturn("admin@earnx.com");
            SecurityContext ctx = mock(SecurityContext.class);
            when(ctx.getAuthentication()).thenReturn(auth);
            SecurityContextHolder.setContext(ctx);
        }

        @AfterEach
        void clearSecurityContext() {
            SecurityContextHolder.clearContext();
        }

        @Test
        @DisplayName("happy path — totalCredits increased, audit log saved")
        void happyPath() {
            stubFindUser();
            stubLockedReward();

            service.adminCredit(1L, 300L, "Bonus campaign");

            ArgumentCaptor<UserReward> rewardCaptor = ArgumentCaptor.forClass(UserReward.class);
            verify(rewardRepo).save(rewardCaptor.capture());
            assertThat(rewardCaptor.getValue().getTotalCredits()).isEqualTo(800L);

            verify(adminAuditRepo).save(argThat(log ->
                    log.getActionType().equals("MANUAL_CREDIT") &&
                    log.getTargetUserId().equals(1L) &&
                    log.getAmount().equals(300L)
            ));
        }

        @Test
        @DisplayName("zero credits → BadRequestException")
        void zero_throws() {
            assertThatThrownBy(() -> service.adminCredit(1L, 0L, "reason"))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Credits must be positive");
        }

        @Test
        @DisplayName("negative credits → BadRequestException")
        void negative_throws() {
            assertThatThrownBy(() -> service.adminCredit(1L, -100L, "reason"))
                    .isInstanceOf(BadRequestException.class);
        }
    }

    // =========================================================================
    //  adminDebit
    // =========================================================================

    @Nested
    @DisplayName("adminDebit()")
    class AdminDebit {

        @BeforeEach
        void stubSecurityContext() {
            Authentication auth = mock(Authentication.class);
            when(auth.getName()).thenReturn("admin@earnx.com");
            SecurityContext ctx = mock(SecurityContext.class);
            when(ctx.getAuthentication()).thenReturn(auth);
            SecurityContextHolder.setContext(ctx);
        }

        @AfterEach
        void clearSecurityContext() {
            SecurityContextHolder.clearContext();
        }

        @Test
        @DisplayName("happy path — totalCredits decreased, audit log saved")
        void happyPath() {
            stubFindUser();
            stubLockedReward(); // total=500

            service.adminDebit(1L, 200L, "Penalty");

            ArgumentCaptor<UserReward> rewardCaptor = ArgumentCaptor.forClass(UserReward.class);
            verify(rewardRepo).save(rewardCaptor.capture());
            assertThat(rewardCaptor.getValue().getTotalCredits()).isEqualTo(300L);

            verify(adminAuditRepo).save(argThat(log ->
                    log.getActionType().equals("MANUAL_DEBIT") &&
                    log.getAmount().equals(200L)
            ));
        }

        @Test
        @DisplayName("debit more than available → BadRequestException")
        void insufficientForDebit_throws() {
            stubFindUser();
            stubLockedReward(); // available = 500

            assertThatThrownBy(() -> service.adminDebit(1L, 600L, "reason"))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Cannot debit");
        }

        @Test
        @DisplayName("debit exactly available → succeeds (boundary)")
        void exactDebit_succeeds() {
            stubFindUser();
            stubLockedReward(); // available = 500

            service.adminDebit(1L, 500L, "exact debit");
            verify(rewardRepo).save(any());
        }

        @Test
        @DisplayName("zero credits → BadRequestException")
        void zero_throws() {
            assertThatThrownBy(() -> service.adminDebit(1L, 0L, "reason"))
                    .isInstanceOf(BadRequestException.class);
        }
    }

    // =========================================================================
    //  getTransactions
    // =========================================================================

    @Nested
    @DisplayName("getTransactions()")
    class GetTransactions {

        @Test
        @DisplayName("returns paginated DTOs correctly mapped")
        void returnsMappedDTOs() {
            RewardTransaction tx = buildPendingTx("REF-PAGE", 100L);
            tx.setType(RewardTxType.EARN);
            tx.setSource(RewardSource.SPIN);
            tx.setStatus(RewardTxStatus.COMPLETED);

            Page<RewardTransaction> page = new PageImpl<>(
                    List.of(tx), PageRequest.of(0, 10), 1);
            when(txRepo.findByUser_Id(eq(1L), any(Pageable.class))).thenReturn(page);

            PageResponse<RewardTransactionDTO> result = service.getTransactions(1L, 0, 10);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getTotalElements()).isEqualTo(1);
            assertThat(result.getPage()).isZero();
            assertThat(result.getContent().get(0).getCredits()).isEqualTo(100L);
            assertThat(result.getContent().get(0).getSource()).isEqualTo(RewardSource.SPIN);
        }

        @Test
        @DisplayName("empty page — content is empty list, not null")
        void emptyPage_returnsEmptyList() {
            Page<RewardTransaction> emptyPage = Page.empty(PageRequest.of(0, 10));
            when(txRepo.findByUser_Id(anyLong(), any(Pageable.class))).thenReturn(emptyPage);

            PageResponse<RewardTransactionDTO> result = service.getTransactions(1L, 0, 10);

            assertThat(result.getContent()).isEmpty();
        }
    }

    // =========================================================================
    //  Private helpers
    // =========================================================================

    private void stubFindUser() {
        when(userRepo.findById(1L)).thenReturn(Optional.of(testUser));
    }

    private void stubActiveConfig(RewardConfig config) {
        when(configRepo.findFirstByIsActiveTrue()).thenReturn(Optional.of(config));
    }

    private void stubDailyEarned(Long earned) {
        when(txRepo.sumEarnedSince(anyLong(), any(OffsetDateTime.class))).thenReturn(earned);
    }

    private void stubLockedReward() {
        when(rewardRepo.findByUserId(1L)).thenReturn(Optional.of(testReward));
        when(rewardRepo.findByUserIdForUpdate(1L)).thenReturn(Optional.of(testReward));
        when(userRepo.findById(1L)).thenReturn(Optional.of(testUser));
    }

    private RewardTransaction buildPendingTx(String referenceId, Long credits) {
        RewardTransaction tx = RewardTransaction.builder()
                .user(testUser)
                .credits(credits)
                .type(RewardTxType.REDEEM)
                .source(RewardSource.BONUS)
                .description("test tx")
                .status(RewardTxStatus.PENDING)
                .referenceId(referenceId)
                .build();
        ReflectionTestUtils.setField(tx, "id", 10L);
        return tx;
    }
}