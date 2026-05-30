package com.myworld.modules.referral.application;

import com.myworld.core.dto.PageResponse;
import com.myworld.modules.identity.domain.User;
import com.myworld.modules.referral.api.ReferralResponseDTO;
import com.myworld.modules.referral.domain.Referral;
import com.myworld.modules.referral.domain.ReferralStatus;
import com.myworld.modules.referral.infrastructure.ReferralRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReferralServiceImpl — Unit Tests")
class ReferralServiceImplTest {

    @Mock ReferralRepository referralRepo;
    @InjectMocks ReferralServiceImpl service;

    private User referrer;
    private User referred;

    @BeforeEach
    void setUp() {
        referrer = User.builder().email("ref@earnx.com").name("Referrer").password("x").build();
        referrer.setId(1L);
        referred = User.builder().email("new@earnx.com").name("New User").password("x").build();
        referred.setId(2L);
    }

    @Nested @DisplayName("getMyReferrals()")
    class GetMyReferrals {

        @Test @DisplayName("returns paginated list of referrals correctly mapped")
        void returnsPagedReferrals() {
            Referral r = Referral.builder()
                    .referrer(referrer).referred(referred)
                    .status(ReferralStatus.SUCCESS).bonusGiven(true).build();
            ReflectionTestUtils.setField(r, "id", 10L);

            Page<Referral> page = new PageImpl<>(List.of(r), PageRequest.of(0, 10), 1);
            when(referralRepo.findByReferrerIdOrderByCreatedAtDesc(eq(1L), any(Pageable.class)))
                    .thenReturn(page);

            PageResponse<ReferralResponseDTO> result = service.getMyReferrals(1L, 0, 10);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getTotalElements()).isEqualTo(1);
            assertThat(result.getContent().get(0).getReferredUserName()).isEqualTo("New User");
            assertThat(result.getContent().get(0).getReferredUserEmail()).isEqualTo("new@earnx.com");
            assertThat(result.getContent().get(0).getStatus()).isEqualTo("SUCCESS");
        }

        @Test @DisplayName("null referred user → shows N/A (not NullPointerException)")
        void nullReferred_showsNa() {
            Referral r = Referral.builder()
                    .referrer(referrer).referred(null)
                    .status(ReferralStatus.PENDING).bonusGiven(false).build();
            ReflectionTestUtils.setField(r, "id", 11L);

            Page<Referral> page = new PageImpl<>(List.of(r), PageRequest.of(0, 10), 1);
            when(referralRepo.findByReferrerIdOrderByCreatedAtDesc(eq(1L), any(Pageable.class)))
                    .thenReturn(page);

            PageResponse<ReferralResponseDTO> result = service.getMyReferrals(1L, 0, 10);

            assertThat(result.getContent().get(0).getReferredUserName()).isEqualTo("N/A");
            assertThat(result.getContent().get(0).getReferredUserEmail()).isEqualTo("N/A");
        }

        @Test @DisplayName("empty page returns empty list (not null)")
        void emptyPage_returnsEmptyList() {
            Page<Referral> empty = Page.empty(PageRequest.of(0, 10));
            when(referralRepo.findByReferrerIdOrderByCreatedAtDesc(anyLong(), any())).thenReturn(empty);

            PageResponse<ReferralResponseDTO> result = service.getMyReferrals(1L, 0, 10);
            assertThat(result.getContent()).isEmpty();
        }
    }

    @Nested @DisplayName("countSuccessfulReferrals()")
    class CountSuccessful {

        @Test @DisplayName("returns count from repository")
        void returnsCount() {
            when(referralRepo.countByReferrerIdAndStatus(1L, ReferralStatus.SUCCESS)).thenReturn(5L);
            assertThat(service.countSuccessfulReferrals(1L)).isEqualTo(5L);
        }

        @Test @DisplayName("zero referrals returns 0")
        void zeroReferrals_returnsZero() {
            when(referralRepo.countByReferrerIdAndStatus(99L, ReferralStatus.SUCCESS)).thenReturn(0L);
            assertThat(service.countSuccessfulReferrals(99L)).isZero();
        }
    }
}
