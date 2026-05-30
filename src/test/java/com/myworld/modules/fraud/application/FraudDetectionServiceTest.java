package com.myworld.modules.fraud.application;

import com.myworld.core.exception.BadRequestException;
import com.myworld.modules.rewards.infrastructure.RedeemLogRepository;
import com.myworld.modules.rewards.infrastructure.RewardTransactionRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("FraudDetectionService — Unit Tests")
class FraudDetectionServiceTest {

    @Mock RedeemLogRepository           redeemLogRepo;
    @Mock RewardTransactionRepository   txRepo;

    @InjectMocks FraudDetectionService service;

    @BeforeEach
    void injectTimezone() throws Exception {
        var f = FraudDetectionService.class.getDeclaredField("timezoneOffset");
        f.setAccessible(true);
        f.set(service, "+05:30");
    }

    // ── checkSameUPI ──────────────────────────────────────────────────────────

    @Nested @DisplayName("checkSameUPI()")
    class CheckSameUpiTests {

        @Test @DisplayName("passes when no other user has same UPI")
        void noOtherUser_passes() {
            when(redeemLogRepo.countOtherUsersWithSamePaymentDetails("user@upi", 1L)).thenReturn(0L);
            assertThatCode(() -> service.checkSameUPI(1L, "user@upi")).doesNotThrowAnyException();
        }

        @Test @DisplayName("throws when another user has same UPI")
        void otherUserSameUpi_throws() {
            when(redeemLogRepo.countOtherUsersWithSamePaymentDetails("shared@upi", 1L)).thenReturn(1L);
            assertThatThrownBy(() -> service.checkSameUPI(1L, "shared@upi"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("already associated");
        }

        @Test @DisplayName("normalises UPI to lowercase before checking")
        void normalisesToLowercase() {
            when(redeemLogRepo.countOtherUsersWithSamePaymentDetails("user@upi", 1L)).thenReturn(0L);
            assertThatCode(() -> service.checkSameUPI(1L, "USER@UPI")).doesNotThrowAnyException();
            verify(redeemLogRepo).countOtherUsersWithSamePaymentDetails("user@upi", 1L);
        }

        @Test @DisplayName("trims whitespace before checking")
        void trimsWhitespace() {
            when(redeemLogRepo.countOtherUsersWithSamePaymentDetails("user@upi", 1L)).thenReturn(0L);
            assertThatCode(() -> service.checkSameUPI(1L, "  user@UPI  ")).doesNotThrowAnyException();
            verify(redeemLogRepo).countOtherUsersWithSamePaymentDetails("user@upi", 1L);
        }

        @Test @DisplayName("skips check when UPI is null")
        void nullUpi_skips() {
            assertThatCode(() -> service.checkSameUPI(1L, null)).doesNotThrowAnyException();
            verifyNoInteractions(redeemLogRepo);
        }

        @Test @DisplayName("skips check when UPI is blank")
        void blankUpi_skips() {
            assertThatCode(() -> service.checkSameUPI(1L, "   ")).doesNotThrowAnyException();
            verifyNoInteractions(redeemLogRepo);
        }
    }

    // ── checkDailyEarnCap ─────────────────────────────────────────────────────

    @Nested @DisplayName("checkDailyEarnCap()")
    class CheckDailyEarnCapTests {

        @Test @DisplayName("passes when under daily cap")
        void underCap_passes() {
            when(txRepo.sumEarnedSince(eq(1L), any(OffsetDateTime.class))).thenReturn(500L);
            assertThatCode(() -> service.checkDailyEarnCap(1L, 100L, 1000L))
                .doesNotThrowAnyException();
        }

        @Test @DisplayName("passes when exactly at daily cap")
        void exactlyCap_passes() {
            when(txRepo.sumEarnedSince(eq(1L), any(OffsetDateTime.class))).thenReturn(900L);
            assertThatCode(() -> service.checkDailyEarnCap(1L, 100L, 1000L))
                .doesNotThrowAnyException();
        }

        @Test @DisplayName("throws when new credits would exceed daily cap")
        void exceedsCap_throws() {
            when(txRepo.sumEarnedSince(eq(1L), any(OffsetDateTime.class))).thenReturn(950L);
            assertThatThrownBy(() -> service.checkDailyEarnCap(1L, 100L, 1000L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Daily earn limit");
        }

        @Test @DisplayName("treats null DB result as zero earned today")
        void nullDbResult_treatedAsZero() {
            when(txRepo.sumEarnedSince(eq(1L), any(OffsetDateTime.class))).thenReturn(null);
            assertThatCode(() -> service.checkDailyEarnCap(1L, 100L, 1000L))
                .doesNotThrowAnyException();
        }
    }
}