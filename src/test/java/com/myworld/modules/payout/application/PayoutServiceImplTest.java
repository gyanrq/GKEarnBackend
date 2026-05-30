package com.myworld.modules.payout.application;

import com.myworld.core.exception.BadRequestException;
import com.myworld.core.exception.ResourceNotFoundException;
import com.myworld.modules.identity.domain.User;
import com.myworld.modules.identity.infrastructure.UserRepository;
import com.myworld.modules.notification.api.NotificationEvent;
import com.myworld.modules.payout.api.PayoutDTO;
import com.myworld.modules.payout.domain.PaymentStatus;
import com.myworld.modules.payout.domain.PayoutRequest;
import com.myworld.modules.payout.infrastructure.PayoutRepository;
import com.myworld.modules.rewards.application.RewardService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;
import com.myworld.modules.fraud.application.RiskScoringService;
import com.myworld.modules.fraud.application.RiskResult;
import org.springframework.context.ApplicationEventPublisher;
import jakarta.servlet.http.HttpServletRequest;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PayoutServiceImpl — Unit Tests")
class PayoutServiceImplTest {

    @Mock PayoutRepository          payoutRepo;
    @Mock UserRepository            userRepo;
    @Mock RewardService             rewardService;
    @Mock ApplicationEventPublisher eventPublisher;
    @Mock RazorpayPayoutClient      razorpayClient;
    @Mock RiskScoringService        riskScoringService;
    @Mock HttpServletRequest        request;

    @InjectMocks PayoutServiceImpl service;

    private User         verifiedUser;
    private PayoutDTO    validDto;
    private PayoutRequest pendingPayout;

    @BeforeEach
    void setUp() throws Exception {
        ReflectionTestUtils.setField(service, "conversionRate", BigDecimal.TEN);
        ReflectionTestUtils.setField(service, "maxRedeemsPerDay", 5L);
        ReflectionTestUtils.setField(service, "maxRupeesPerDay", new BigDecimal("5000"));
        ReflectionTestUtils.setField(service, "fraudBlockThreshold", 500);
        ReflectionTestUtils.setField(service, "fraudHoldThreshold", 200);

        when(riskScoringService.scoreUser(anyLong(), any())).thenReturn(new RiskResult(0, false, false, java.util.Collections.emptyList()));

        verifiedUser = User.builder()
                .email("user@earnx.com").name("Test User")
                .password("hashed").phone("9999999999").build();
        verifiedUser.setId(42L);
        verifiedUser.setIsEmailVerified(true);
        verifiedUser.setIsPhoneVerified(true);

        validDto = PayoutDTO.builder()
                .amount(new BigDecimal("100"))
                .payoutType("UPI")
                .payoutDetails("user@upi")
                .idempotencyKey("idem-001")
                .build();

        pendingPayout = PayoutRequest.builder()
                .user(verifiedUser)
                .amount(new BigDecimal("100"))
                .payoutType("UPI")
                .paymentDetails("user@upi")
                .status(PaymentStatus.PENDING)
                .transactionRef("PAYOUT-ref-001")
                .build();
        pendingPayout.setId(100L);
    }

    // ── requestPayout ─────────────────────────────────────────────────────────

    @Nested @DisplayName("requestPayout()")
    class RequestPayoutTests {

        @BeforeEach
        void mocks() {
            when(userRepo.findById(42L)).thenReturn(Optional.of(verifiedUser));
            when(payoutRepo.existsByIdempotencyKey("idem-001")).thenReturn(false);
        }

        @Test @DisplayName("happy path — saves payout and fires notification event")
        void happyPath() {
            service.requestPayout(42L, validDto, request);

            verify(rewardService).redeemCredits(
                eq(42L), eq(1000L), anyString(), anyString());    // 100 * 10 = 1000 credits
            verify(payoutRepo).save(argThat(r ->
                r.getStatus() == PaymentStatus.PENDING &&
                r.getAmount().compareTo(new BigDecimal("100")) == 0));
            verify(eventPublisher).publishEvent(any(NotificationEvent.class));
        }

        @Test @DisplayName("rejects duplicate idempotency key")
        void duplicateIdempotency_throws() {
            when(payoutRepo.existsByIdempotencyKey("idem-001")).thenReturn(true);

            assertThatThrownBy(() -> service.requestPayout(42L, validDto, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("already been processed");
        }

        @Test @DisplayName("throws when email not verified")
        void emailNotVerified_throws() {
            verifiedUser.setIsEmailVerified(false);

            assertThatThrownBy(() -> service.requestPayout(42L, validDto, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Email verification");
        }

        @Test @DisplayName("throws when phone not verified")
        void phoneNotVerified_throws() {
            verifiedUser.setIsPhoneVerified(false);

            assertThatThrownBy(() -> service.requestPayout(42L, validDto, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Phone number verification");
        }

        @Test @DisplayName("throws when phone is null")
        void phoneNull_throws() {
            verifiedUser.setPhone(null);
            verifiedUser.setIsPhoneVerified(false);

            assertThatThrownBy(() -> service.requestPayout(42L, validDto, request))
                .isInstanceOf(BadRequestException.class);
        }

        @Test @DisplayName("throws when user not found")
        void userNotFound_throws() {
            when(userRepo.findById(42L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.requestPayout(42L, validDto, request))
                .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test @DisplayName("correct credit calculation: amount * conversionRate")
        void creditCalculation_correct() {
            ArgumentCaptor<Long> creditsCaptor = ArgumentCaptor.forClass(Long.class);
            service.requestPayout(42L, validDto, request);    // amount = 100, rate = 10

            verify(rewardService).redeemCredits(eq(42L), creditsCaptor.capture(), any(), any());
            assertThat(creditsCaptor.getValue()).isEqualTo(1000L);
        }

        @Test @DisplayName("skips idempotency check when key is null")
        void nullIdempotencyKey_skipsCheck() {
            validDto.setIdempotencyKey(null);

            assertThatCode(() -> service.requestPayout(42L, validDto, request))
                .doesNotThrowAnyException();
            verify(payoutRepo, never()).existsByIdempotencyKey(any());
        }
    }

    // ── approvePayout ─────────────────────────────────────────────────────────

    @Nested @DisplayName("approvePayout()")
    class ApprovePayoutTests {

        @BeforeEach
        void mocks() {
            when(payoutRepo.findById(100L)).thenReturn(Optional.of(pendingPayout));
        }

        @Test @DisplayName("sets status to APPROVED and notifies")
        void approvesSuccessfully() {
            service.approvePayout(100L, "admin@earnx.com");

            assertThat(pendingPayout.getStatus()).isEqualTo(PaymentStatus.APPROVED);
            assertThat(pendingPayout.getProcessedBy()).isEqualTo("admin@earnx.com");
            verify(rewardService).approveRedeem("PAYOUT-ref-001");
            verify(eventPublisher).publishEvent(any(NotificationEvent.class));
        }

        @Test @DisplayName("throws when payout not found")
        void notFound_throws() {
            when(payoutRepo.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.approvePayout(999L, "admin@earnx.com"))
                .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test @DisplayName("throws when payout is not PENDING")
        void notPending_throws() {
            pendingPayout.setStatus(PaymentStatus.APPROVED);

            assertThatThrownBy(() -> service.approvePayout(100L, "admin@earnx.com"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("PENDING");
        }
    }

    // ── rejectPayout ──────────────────────────────────────────────────────────

    @Nested @DisplayName("rejectPayout()")
    class RejectPayoutTests {

        @BeforeEach
        void mocks() {
            when(payoutRepo.findById(100L)).thenReturn(Optional.of(pendingPayout));
        }

        @Test @DisplayName("sets status to REJECTED, refunds credits, notifies")
        void rejectsSuccessfully() {
            service.rejectPayout(100L, "Fraud detected", "admin@earnx.com");

            assertThat(pendingPayout.getStatus()).isEqualTo(PaymentStatus.REJECTED);
            assertThat(pendingPayout.getAdminNotes()).isEqualTo("Fraud detected");
            verify(rewardService).rejectRedeem("PAYOUT-ref-001");
            verify(eventPublisher).publishEvent(any(NotificationEvent.class));
        }

        @Test @DisplayName("rejection with null reason uses fallback message")
        void nullReason_usedFallback() {
            // Should not throw — fallback message used in notification
            assertThatCode(() -> service.rejectPayout(100L, null, "admin@earnx.com"))
                .doesNotThrowAnyException();
        }

        @Test @DisplayName("throws when payout not in PENDING status")
        void notPending_throws() {
            pendingPayout.setStatus(PaymentStatus.REJECTED);

            assertThatThrownBy(() -> service.rejectPayout(100L, "reason", "admin@earnx.com"))
                .isInstanceOf(BadRequestException.class);
        }
    }

    // ── markPaid ──────────────────────────────────────────────────────────────

    @Nested @DisplayName("markPaid()")
    class MarkPaidTests {

        @BeforeEach
        void mocks() {
            pendingPayout.setStatus(PaymentStatus.APPROVED);
            when(payoutRepo.findById(100L)).thenReturn(Optional.of(pendingPayout));
        }

        @Test @DisplayName("sets status to PAID and notifies")
        void marksPaid() {
            service.markPaid(100L, "RAZORPAY-TX-789", "admin@earnx.com");

            assertThat(pendingPayout.getStatus()).isEqualTo(PaymentStatus.PAID);
            assertThat(pendingPayout.getTransactionRef()).isEqualTo("RAZORPAY-TX-789");
            verify(eventPublisher).publishEvent(any(NotificationEvent.class));
        }

        @Test @DisplayName("throws when payout not found")
        void notFound_throws() {
            when(payoutRepo.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.markPaid(999L, "ref", "admin@earnx.com"))
                .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test @DisplayName("throws when status is not APPROVED")
        void notApproved_throws() {
            pendingPayout.setStatus(PaymentStatus.PENDING);

            assertThatThrownBy(() -> service.markPaid(100L, "ref", "admin@earnx.com"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("APPROVED");
        }
    }
}
