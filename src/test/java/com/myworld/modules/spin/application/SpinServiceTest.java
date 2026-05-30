package com.myworld.modules.spin.application;

import com.myworld.core.exception.BadRequestException;
import com.myworld.core.exception.ResourceNotFoundException;
import com.myworld.modules.identity.domain.User;
import com.myworld.modules.identity.infrastructure.UserRepository;
import com.myworld.modules.rewards.application.RewardService;
import com.myworld.modules.rewards.domain.RewardSource;
import com.myworld.modules.spin.domain.SpinHistory;
import com.myworld.modules.spin.domain.SpinPrize;
import com.myworld.modules.spin.infrastructure.SpinHistoryRepository;
import com.myworld.modules.spin.infrastructure.SpinPrizeRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SpinService — Unit Tests")
class SpinServiceTest {

    @Mock SpinHistoryRepository spinRepo;
    @Mock SpinPrizeRepository   spinPrizeRepo;
    @Mock RewardService         rewardService;
    @Mock UserRepository        userRepo;

    @InjectMocks SpinService service;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
            .email("user@earnx.com").name("Test User").password("hashed").build();
        testUser.setId(1L);
    }

    @Test @DisplayName("dailySpin — awards credits and saves history on first spin")
    void dailySpin_firstSpin_awardsCredits() {
        when(spinRepo.existsByUserIdAndCreatedAtAfter(eq(1L), any(OffsetDateTime.class)))
            .thenReturn(false);
        when(userRepo.findById(1L)).thenReturn(Optional.of(testUser));
        when(spinPrizeRepo.findByIsActiveTrueOrderBySortOrderAsc()).thenReturn(List.of());
        when(spinRepo.save(any(SpinHistory.class))).thenAnswer(inv -> inv.getArgument(0));

        long credits = service.dailySpin(1L);

        assertThat(credits).isGreaterThan(0);
        verify(spinRepo).save(argThat(h -> h.getCreditsWon() == credits));
        verify(rewardService).earnCredits(eq(1L), eq(credits), contains("Spin"), eq(RewardSource.SPIN));
    }

    @Test @DisplayName("dailySpin — throws when user already spun today")
    void dailySpin_alreadySpun_throws() {
        when(spinRepo.existsByUserIdAndCreatedAtAfter(eq(1L), any(OffsetDateTime.class)))
            .thenReturn(true);

        assertThatThrownBy(() -> service.dailySpin(1L))
            .isInstanceOf(BadRequestException.class)
            .hasMessageContaining("already used");
    }

    @Test @DisplayName("dailySpin — throws on concurrent race condition (DB unique violation)")
    void dailySpin_concurrentRace_throws() {
        when(spinRepo.existsByUserIdAndCreatedAtAfter(eq(1L), any(OffsetDateTime.class)))
            .thenReturn(false);
        when(userRepo.findById(1L)).thenReturn(Optional.of(testUser));
        when(spinPrizeRepo.findByIsActiveTrueOrderBySortOrderAsc()).thenReturn(List.of());
        when(spinRepo.save(any())).thenThrow(new DataIntegrityViolationException("dup key"));

        assertThatThrownBy(() -> service.dailySpin(1L))
            .isInstanceOf(BadRequestException.class)
            .hasMessageContaining("already used");
    }

    @Test @DisplayName("dailySpin — throws when user not found")
    void dailySpin_userNotFound_throws() {
        when(spinRepo.existsByUserIdAndCreatedAtAfter(eq(1L), any(OffsetDateTime.class)))
            .thenReturn(false);
        when(userRepo.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.dailySpin(1L))
            .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test @DisplayName("dailySpin — uses DB prizes when available")
    void dailySpin_usesDbPrizes() {
        SpinPrize prize = SpinPrize.builder()
            .credits(250L).weight(100).isActive(true).sortOrder(1).build();

        when(spinRepo.existsByUserIdAndCreatedAtAfter(eq(1L), any(OffsetDateTime.class)))
            .thenReturn(false);
        when(userRepo.findById(1L)).thenReturn(Optional.of(testUser));
        when(spinPrizeRepo.findByIsActiveTrueOrderBySortOrderAsc()).thenReturn(List.of(prize));
        when(spinRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        long credits = service.dailySpin(1L);

        // Only prize has 100% weight, must always win this prize
        assertThat(credits).isEqualTo(250L);
    }

    @Test @DisplayName("getActivePrizes — returns active prizes list")
    void getActivePrizes_returnsList() {
        SpinPrize prize = SpinPrize.builder().credits(100L).weight(50).isActive(true).build();
        when(spinPrizeRepo.findByIsActiveTrueOrderBySortOrderAsc()).thenReturn(List.of(prize));

        List<SpinPrize> result = service.getActivePrizes();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCredits()).isEqualTo(100L);
    }
}