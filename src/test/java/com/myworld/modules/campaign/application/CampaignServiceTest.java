package com.myworld.modules.campaign.application;

import com.myworld.core.exception.BadRequestException;
import com.myworld.core.exception.ResourceNotFoundException;
import com.myworld.modules.campaign.domain.*;
import com.myworld.modules.campaign.dto.LeadSubmitRequest;
import com.myworld.modules.campaign.infrastructure.CampaignRepository;
import com.myworld.modules.campaign.infrastructure.LeadRepository;
import com.myworld.modules.identity.domain.User;
import com.myworld.modules.identity.infrastructure.UserRepository;
import com.myworld.modules.notification.application.NotificationService;
import com.myworld.modules.rewards.application.RewardService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("CampaignService — submitLead()")
class CampaignServiceTest {

    @Mock private CampaignRepository    campaignRepo;
    @Mock private LeadRepository        leadRepo;
    @Mock private UserRepository        userRepo;
    @Mock private RewardService         rewardService;
    @Mock private NotificationService   notificationService;

    @InjectMocks
    private CampaignService service;

    private User         testUser;
    private Campaign     activeCampaign;
    private LeadSubmitRequest validReq;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "maxDailyLeadsPerUser", 5);

        testUser = User.builder()
                .email("user@earnx.com").name("Test").password("x").build();
        ReflectionTestUtils.setField(testUser, "id", 1L);

        activeCampaign = Campaign.builder()
                .name("Test Campaign")
                .isActive(true)
                .maxLeadsPerUser(1)
                .totalLeadsCap(0)   // unlimited
                .rewardAmount(BigDecimal.TEN)
                .build();
        ReflectionTestUtils.setField(activeCampaign, "id", 10L);

        validReq = LeadSubmitRequest.builder()
                .campaignId(10L)
                .registeredMobile("9999999999")
                .registeredEmail("user@earnx.com")
                .build();

        when(userRepo.findById(1L)).thenReturn(Optional.of(testUser));
        when(campaignRepo.findById(10L)).thenReturn(Optional.of(activeCampaign));
        when(leadRepo.countByUserIdAndStatus(1L, LeadStatus.PENDING)).thenReturn(0L);
        when(leadRepo.countByUserIdAndCampaignId(1L, 10L)).thenReturn(0L);
        when(leadRepo.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Nested
    @DisplayName("Happy path")
    class HappyPath {

        @Test
        @DisplayName("valid request — lead saved and notification sent")
        void savesLeadAndNotifies() {
            service.submitLead(1L, validReq);

            verify(leadRepo).saveAndFlush(argThat(lead ->
                lead.getStatus() == LeadStatus.PENDING &&
                lead.getCampaign().getId().equals(10L)
            ));
            verify(notificationService).sendNotification(eq(1L), any(), any(), any());
        }
    }

    @Nested
    @DisplayName("Campaign validation")
    class CampaignValidation {

        @Test
        @DisplayName("inactive campaign — throws BadRequestException")
        void inactiveCampaign_throws() {
            activeCampaign.setIsActive(false);

            assertThatThrownBy(() -> service.submitLead(1L, validReq))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("not active");
        }

        @Test
        @DisplayName("campaign not started yet — throws")
        void campaignNotStarted_throws() {
            activeCampaign.setStartAt(OffsetDateTime.now().plusDays(1));

            assertThatThrownBy(() -> service.submitLead(1L, validReq))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("not started");
        }

        @Test
        @DisplayName("campaign ended — throws")
        void campaignEnded_throws() {
            activeCampaign.setEndAt(OffsetDateTime.now().minusDays(1));

            assertThatThrownBy(() -> service.submitLead(1L, validReq))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("ended");
        }

        @Test
        @DisplayName("campaign not found — throws ResourceNotFoundException")
        void campaignNotFound_throws() {
            when(campaignRepo.findById(10L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.submitLead(1L, validReq))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("Lead cap enforcement")
    class LeadCaps {

        @Test
        @DisplayName("per-campaign user cap exceeded — throws")
        void perCampaignCap_exceeded_throws() {
            when(leadRepo.countByUserIdAndCampaignId(1L, 10L)).thenReturn(1L);
            // maxLeadsPerUser = 1

            assertThatThrownBy(() -> service.submitLead(1L, validReq))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("maximum leads");
        }

        @Test
        @DisplayName("total campaign cap reached — throws")
        void totalCap_reached_throws() {
            activeCampaign.setTotalLeadsCap(100);
            when(leadRepo.countByCampaignId(10L)).thenReturn(100L);

            assertThatThrownBy(() -> service.submitLead(1L, validReq))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("maximum number of leads");
        }

        @Test
        @DisplayName("totalLeadsCap=0 means unlimited — does not check count")
        void zeroCap_isUnlimited() {
            activeCampaign.setTotalLeadsCap(0);

            service.submitLead(1L, validReq);

            verify(leadRepo, never()).countByCampaignId(anyLong());
        }

        @Test
        @DisplayName("global daily pending lead cap exceeded — throws")
        void globalDailyPendingCap_exceeded_throws() {
            when(leadRepo.countByUserIdAndStatus(1L, LeadStatus.PENDING)).thenReturn(5L);
            // maxDailyLeadsPerUser = 5

            assertThatThrownBy(() -> service.submitLead(1L, validReq))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("pending leads");
        }

        @Test
        @DisplayName("global pending cap configurable — 5 leads ok when cap is 10")
        void globalCap_notExceeded_proceeds() {
            ReflectionTestUtils.setField(service, "maxDailyLeadsPerUser", 10);
            when(leadRepo.countByUserIdAndStatus(1L, LeadStatus.PENDING)).thenReturn(5L);

            service.submitLead(1L, validReq);

            verify(leadRepo).saveAndFlush(any());
        }
    }

    @Nested
    @DisplayName("User validation")
    class UserValidation {

        @Test
        @DisplayName("user not found — throws ResourceNotFoundException")
        void userNotFound_throws() {
            when(userRepo.findById(1L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.submitLead(1L, validReq))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }
}
