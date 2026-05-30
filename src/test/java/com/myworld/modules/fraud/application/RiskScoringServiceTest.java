package com.myworld.modules.fraud.application;

import com.myworld.modules.identity.domain.User;
import com.myworld.modules.identity.infrastructure.DeviceFingerprintRepository;
import com.myworld.modules.identity.infrastructure.UserRepository;
import com.myworld.modules.referral.infrastructure.ReferralRepository;
import com.myworld.modules.rewards.infrastructure.RedeemLogRepository;
import com.myworld.modules.rewards.infrastructure.RewardTransactionRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("RiskScoringService — Unit Tests")
class RiskScoringServiceTest {

    @Mock UserRepository              userRepo;
    @Mock DeviceFingerprintRepository fingerprintRepo;
    @Mock RedeemLogRepository         redeemLogRepo;
    @Mock RewardTransactionRepository txRepo;
    @Mock ReferralRepository          referralRepo;
    @Mock IpIntelligenceService       ipIntelligenceService;

    @InjectMocks RiskScoringService service;

    private User         cleanUser;
    private HttpServletRequest request;

    @BeforeEach
    void setUp() throws Exception {
        injectConfig("+05:30", 200, 500, 3, 2, 60, 2);

        cleanUser = User.builder()
            .email("clean@earnx.com").name("Clean User").password("hashed").build();
        cleanUser.setId(1L);
        cleanUser.setCreatedAt(OffsetDateTime.now().minusDays(30)); // old account

        request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn("10.0.0.1");
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getHeader("X-Device-ID")).thenReturn(null);
        when(request.getHeader("X-Is-VPN")).thenReturn(null);

        // Clean user — no fraud signals
        when(userRepo.countActiveUsersWithSameIp(anyString(), eq(1L))).thenReturn(0L);
        when(redeemLogRepo.countOtherUsersWithSamePaymentDetails(any(), eq(1L))).thenReturn(0L);
        when(txRepo.countEarnTransactionsByUser(1L)).thenReturn(10L);
        when(redeemLogRepo.countByUserIdAndCreatedAtAfter(eq(1L), any())).thenReturn(0L);
        when(referralRepo.existsByReferredUserIdAndBonusGiven(1L, true)).thenReturn(false);
    }

    @Test @DisplayName("clean user scores zero — decision CLEAR")
    void cleanUser_scoreZero_clear() {
        RiskResult result = service.evaluate(cleanUser, null, request);

        assertThat(result.getScore()).isEqualTo(0);
        assertThat(result.isClear()).isTrue();
        assertThat(result.isHardBlock()).isFalse();
        assertThat(result.isHold()).isFalse();
        assertThat(result.getDecision()).isEqualTo("CLEAR");
    }

    @Test @DisplayName("same IP shared with many users adds SAME_IP flag")
    void sameIp_addsFlag() {
        when(userRepo.countActiveUsersWithSameIp(anyString(), eq(1L))).thenReturn(5L); // > maxSharedIp=3

        RiskResult result = service.evaluate(cleanUser, null, request);

        assertThat(result.getFlags()).anyMatch(f -> f.startsWith("SAME_IP"));
        assertThat(result.getScore()).isGreaterThan(0);
    }

    @Test @DisplayName("shared UPI adds SHARED_UPI flag")
    void sharedUpi_addsFlag() {
        when(redeemLogRepo.countOtherUsersWithSamePaymentDetails("shared@upi", 1L)).thenReturn(1L);

        RiskResult result = service.evaluate(cleanUser, "shared@upi", request);

        assertThat(result.getFlags()).anyMatch(f -> f.startsWith("SHARED_UPI"));
        assertThat(result.getScore()).isGreaterThan(0);
    }

    @Test @DisplayName("new account (fast signup) adds FAST_SIGNUP flag")
    void fastSignup_addsFlag() {
        cleanUser.setCreatedAt(OffsetDateTime.now().minusSeconds(10)); // 10s old < threshold 60s

        RiskResult result = service.evaluate(cleanUser, null, request);

        assertThat(result.getFlags()).anyMatch(f -> f.startsWith("FAST_SIGNUP"));
    }

    @Test @DisplayName("no trade activity adds NO_TRADE_ACTIVITY flag")
    void noTrade_addsFlag() {
        when(txRepo.countEarnTransactionsByUser(1L)).thenReturn(0L);

        RiskResult result = service.evaluate(cleanUser, null, request);

        assertThat(result.getFlags()).contains("NO_TRADE_ACTIVITY");
    }

    @Test @DisplayName("IP Intelligence flags VPN_PROXY")
    void vpn_addsFlag() {
        when(ipIntelligenceService.isVpnOrProxy(anyString())).thenReturn(true);

        RiskResult result = service.evaluate(cleanUser, null, request);

        assertThat(result.getFlags()).contains("VPN_PROXY");
    }

    @Test @DisplayName("referral bonus with no trades adds REFERRAL_ABUSE flag")
    void referralAbuse_addsFlag() {
        when(txRepo.countEarnTransactionsByUser(1L)).thenReturn(0L);
        when(referralRepo.existsByReferredUserIdAndBonusGiven(1L, true)).thenReturn(true);

        RiskResult result = service.evaluate(cleanUser, null, request);

        assertThat(result.getFlags()).contains("REFERRAL_ABUSE");
    }

    @Test @DisplayName("high score (>=500) results in BLOCK decision")
    void highScore_resultsInBlock() {
        // Stack many risk signals to push score >= 500
        when(userRepo.countActiveUsersWithSameIp(anyString(), eq(1L))).thenReturn(10L);     // +100
        when(txRepo.countEarnTransactionsByUser(1L)).thenReturn(0L);                        // +200
        when(referralRepo.existsByReferredUserIdAndBonusGiven(1L, true)).thenReturn(true);  // +120
        when(redeemLogRepo.countOtherUsersWithSamePaymentDetails(any(), eq(1L))).thenReturn(1L); // +120
        when(ipIntelligenceService.isVpnOrProxy(anyString())).thenReturn(true);             // +80

        cleanUser.setCreatedAt(OffsetDateTime.now().minusSeconds(5));                       // +100 fast signup

        RiskResult result = service.evaluate(cleanUser, "shared@upi", request);

        assertThat(result.getScore()).isGreaterThanOrEqualTo(500);
        assertThat(result.isHardBlock()).isTrue();
        assertThat(result.getDecision()).isEqualTo("BLOCK");
    }

    @Test @DisplayName("X-Forwarded-For header is used for IP extraction")
    void xForwardedFor_usedForIp() {
        when(request.getHeader("X-Forwarded-For")).thenReturn("203.0.113.5, 10.0.0.1");
        when(userRepo.countActiveUsersWithSameIp(eq("203.0.113.5"), eq(1L))).thenReturn(0L);

        // Should not throw and should use the first IP from the header
        assertThatCode(() -> service.evaluate(cleanUser, null, request))
            .doesNotThrowAnyException();
        verify(userRepo).countActiveUsersWithSameIp(eq("203.0.113.5"), eq(1L));
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private void injectConfig(String tz, int hold, int block, int maxIp,
                               int maxDevice, int fastSignupSecs, int multiWithdraw)
            throws Exception {
        setField("timezoneOffset",    tz);
        setField("holdThreshold",     hold);
        setField("blockThreshold",    block);
        setField("maxSharedIpUsers",  maxIp);
        setField("maxSharedDeviceUsers", maxDevice);
        setField("fastSignupSeconds", fastSignupSecs);
        setField("multiWithdraw24h",  multiWithdraw);
    }

    private void setField(String name, Object value) throws Exception {
        var f = RiskScoringService.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(service, value);
    }
}