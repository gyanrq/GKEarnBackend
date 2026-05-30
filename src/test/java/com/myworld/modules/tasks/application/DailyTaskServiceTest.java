package com.myworld.modules.tasks.application;

import com.myworld.core.exception.BadRequestException;
import com.myworld.core.exception.ResourceNotFoundException;
import com.myworld.modules.identity.domain.User;
import com.myworld.modules.identity.infrastructure.UserRepository;
import com.myworld.modules.rewards.application.RewardService;
import com.myworld.modules.rewards.domain.RewardSource;
import com.myworld.modules.tasks.domain.*;
import com.myworld.modules.tasks.dto.TaskDTO;
import com.myworld.modules.tasks.dto.TaskLeadSubmitDTO;
import com.myworld.modules.tasks.infrastructure.DailyTaskRepository;
import com.myworld.modules.tasks.infrastructure.TaskConfigRepository;
import com.myworld.modules.tasks.infrastructure.TaskLeadRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("DailyTaskService — Unit Tests")
class DailyTaskServiceTest {

    @Mock private DailyTaskRepository  taskRepo;
    @Mock private TaskConfigRepository configRepo;
    @Mock private TaskLeadRepository   leadRepo;
    @Mock private RewardService        rewardService;
    @Mock private UserRepository       userRepo;

    @InjectMocks
    private DailyTaskService service;

    private User testUser;

    @BeforeEach
    void setUp() {
        // FIX: inject timezone config — this is the timezone fix we're also testing
        ReflectionTestUtils.setField(service, "timezoneOffset", "+05:30");

        testUser = User.builder()
                .email("user@earnx.com").name("Test User").password("hashed").build();
        ReflectionTestUtils.setField(testUser, "id", 1L);

        // Default: no completions today, no leads, no DB config
        when(taskRepo.findTodayCompletions(anyLong(), any(OffsetDateTime.class)))
                .thenReturn(List.of());
        when(leadRepo.findAll()).thenReturn(List.of());
        when(configRepo.findAll()).thenReturn(List.of());
    }

    // =========================================================================
    //  completeTask — happy path
    // =========================================================================

    @Nested
    @DisplayName("completeTask()")
    class CompleteTask {

        @Test
        @DisplayName("first-time LOGIN task — awards fallback credits and saves completion")
        void loginTask_firstTime_awardsCredits() {
            when(taskRepo.existsByUserIdAndTaskTypeAndCreatedAtAfter(
                    eq(1L), eq(TaskType.LOGIN), any(OffsetDateTime.class))).thenReturn(false);
            when(userRepo.findById(1L)).thenReturn(Optional.of(testUser));
            when(configRepo.findByTaskType(TaskType.LOGIN)).thenReturn(Optional.empty());
            when(taskRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            boolean result = service.completeTask(1L, TaskType.LOGIN);

            assertThat(result).isTrue();
            verify(taskRepo).save(argThat(c -> c.getTaskType() == TaskType.LOGIN));
            verify(rewardService).earnCredits(eq(1L), eq(10L), anyString(), eq(RewardSource.DAILY_TASK));
        }

        @Test
        @DisplayName("already completed today — returns false, no credits awarded")
        void alreadyCompleted_returnsFalse() {
            when(taskRepo.existsByUserIdAndTaskTypeAndCreatedAtAfter(
                    eq(1L), eq(TaskType.LOGIN), any(OffsetDateTime.class))).thenReturn(true);
            when(configRepo.findByTaskType(TaskType.LOGIN)).thenReturn(Optional.empty());

            boolean result = service.completeTask(1L, TaskType.LOGIN);

            assertThat(result).isFalse();
            verify(rewardService, never()).earnCredits(anyLong(), anyLong(), any(), any());
            verify(taskRepo, never()).save(any());
        }

        @Test
        @DisplayName("disabled task in DB config — throws BadRequestException")
        void disabledTask_throws() {
            TaskConfig disabled = TaskConfig.builder()
                    .taskType(TaskType.SPIN_WHEEL).isActive(false).credits(5L).build();
            when(configRepo.findByTaskType(TaskType.SPIN_WHEEL)).thenReturn(Optional.of(disabled));

            assertThatThrownBy(() -> service.completeTask(1L, TaskType.SPIN_WHEEL))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("disabled");
        }

        @Test
        @DisplayName("LINK_ task without prior lead submission — throws BadRequestException")
        void linkTaskWithoutLead_throws() {
            when(configRepo.findByTaskType(TaskType.LINK_BINANCE)).thenReturn(Optional.empty());
            when(leadRepo.existsByUserIdAndTaskType(1L, TaskType.LINK_BINANCE)).thenReturn(false);

            assertThatThrownBy(() -> service.completeTask(1L, TaskType.LINK_BINANCE))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("submit your details");
        }

        @Test
        @DisplayName("LINK_ task WITH lead submission — completes successfully")
        void linkTaskWithLead_completes() {
            when(configRepo.findByTaskType(TaskType.LINK_BINANCE)).thenReturn(Optional.empty());
            when(leadRepo.existsByUserIdAndTaskType(1L, TaskType.LINK_BINANCE)).thenReturn(true);
            when(taskRepo.existsByUserIdAndTaskTypeAndCreatedAtAfter(
                    eq(1L), eq(TaskType.LINK_BINANCE), any())).thenReturn(false);
            when(userRepo.findById(1L)).thenReturn(Optional.of(testUser));
            when(taskRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            boolean result = service.completeTask(1L, TaskType.LINK_BINANCE);

            assertThat(result).isTrue();
            verify(rewardService).earnCredits(eq(1L), eq(200L), any(), eq(RewardSource.DAILY_TASK));
        }

        @Test
        @DisplayName("DB config credits override fallback credits")
        void dbConfigCredits_overrideFallback() {
            TaskConfig cfg = TaskConfig.builder()
                    .taskType(TaskType.LOGIN).isActive(true).credits(999L).build();
            when(configRepo.findByTaskType(TaskType.LOGIN)).thenReturn(Optional.of(cfg));
            when(taskRepo.existsByUserIdAndTaskTypeAndCreatedAtAfter(
                    eq(1L), eq(TaskType.LOGIN), any())).thenReturn(false);
            when(userRepo.findById(1L)).thenReturn(Optional.of(testUser));
            when(taskRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.completeTask(1L, TaskType.LOGIN);

            verify(rewardService).earnCredits(eq(1L), eq(999L), any(), any());
        }

        @Test
        @DisplayName("user not found — throws ResourceNotFoundException")
        void userNotFound_throws() {
            when(configRepo.findByTaskType(TaskType.LOGIN)).thenReturn(Optional.empty());
            when(taskRepo.existsByUserIdAndTaskTypeAndCreatedAtAfter(
                    eq(1L), eq(TaskType.LOGIN), any())).thenReturn(false);
            when(userRepo.findById(1L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.completeTask(1L, TaskType.LOGIN))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // =========================================================================
    //  completeTask — timezone fix verification
    // =========================================================================

    @Nested
    @DisplayName("completeTask() — timezone config")
    class TimezoneConfig {

        @Test
        @DisplayName("uses timezoneOffset field (not hardcoded UTC) for daily reset boundary")
        void usesConfiguredTimezone_notHardcoded() {
            // Test that todayStartIST() uses the configured timezone.
            // We verify by confirming existsByUserIdAndTaskTypeAndCreatedAtAfter
            // is called with an OffsetDateTime (not null), meaning todayStartIST() ran.
            when(configRepo.findByTaskType(TaskType.LOGIN)).thenReturn(Optional.empty());
            when(taskRepo.existsByUserIdAndTaskTypeAndCreatedAtAfter(
                    eq(1L), eq(TaskType.LOGIN), any(OffsetDateTime.class))).thenReturn(true);

            boolean result = service.completeTask(1L, TaskType.LOGIN);

            assertThat(result).isFalse();
            // Verify the OffsetDateTime passed has +05:30 offset (IST)
            ArgumentCaptor<OffsetDateTime> dtCaptor = ArgumentCaptor.forClass(OffsetDateTime.class);
            verify(taskRepo).existsByUserIdAndTaskTypeAndCreatedAtAfter(
                    eq(1L), eq(TaskType.LOGIN), dtCaptor.capture());
            assertThat(dtCaptor.getValue().getOffset().toString()).isEqualTo("+05:30");
        }

        @Test
        @DisplayName("timezone can be changed to UTC via config — boundary changes accordingly")
        void configurableToUtc() {
            ReflectionTestUtils.setField(service, "timezoneOffset", "Z");
            when(configRepo.findByTaskType(TaskType.LOGIN)).thenReturn(Optional.empty());
            when(taskRepo.existsByUserIdAndTaskTypeAndCreatedAtAfter(
                    eq(1L), eq(TaskType.LOGIN), any(OffsetDateTime.class))).thenReturn(true);

            service.completeTask(1L, TaskType.LOGIN);

            ArgumentCaptor<OffsetDateTime> dtCaptor = ArgumentCaptor.forClass(OffsetDateTime.class);
            verify(taskRepo).existsByUserIdAndTaskTypeAndCreatedAtAfter(
                    eq(1L), eq(TaskType.LOGIN), dtCaptor.capture());
            assertThat(dtCaptor.getValue().getOffset().toString()).isEqualTo("Z");
        }
    }

    // =========================================================================
    //  submitLead
    // =========================================================================

    @Nested
    @DisplayName("submitLead()")
    class SubmitLead {

        @Test
        @DisplayName("happy path — saves lead and returns partner URL")
        void savesLeadAndReturnsUrl() {
            when(leadRepo.findByUserIdAndTaskType(1L, TaskType.LINK_GROWW))
                    .thenReturn(Optional.empty());
            when(userRepo.findById(1L)).thenReturn(Optional.of(testUser));
            when(configRepo.findByTaskType(TaskType.LINK_GROWW)).thenReturn(Optional.empty());
            when(leadRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            TaskLeadSubmitDTO dto = TaskLeadSubmitDTO.builder()
                    .taskType("LINK_GROWW")
                    .email("user@earnx.com")
                    .mobile("9999999999")
                    .build();

            String url = service.submitLead(1L, dto);

            assertThat(url).contains("groww.in");
            verify(leadRepo).save(any(TaskLead.class));
        }

        @Test
        @DisplayName("idempotent — second submitLead returns same URL without saving again")
        void idempotent_returnsSameUrl() {
            TaskLead existing = TaskLead.builder()
                    .user(testUser).taskType(TaskType.LINK_ZERODHA)
                    .email("u@e.com").partnerUrl("https://zerodha.com/existing").build();
            when(leadRepo.findByUserIdAndTaskType(1L, TaskType.LINK_ZERODHA))
                    .thenReturn(Optional.of(existing));

            TaskLeadSubmitDTO dto = TaskLeadSubmitDTO.builder()
                    .taskType("LINK_ZERODHA").email("u@e.com").mobile("9999").build();

            String url = service.submitLead(1L, dto);

            assertThat(url).isEqualTo("https://zerodha.com/existing");
            verify(leadRepo, never()).save(any());
        }

        @Test
        @DisplayName("invalid taskType string — throws BadRequestException")
        void invalidTaskType_throws() {
            TaskLeadSubmitDTO dto = TaskLeadSubmitDTO.builder()
                    .taskType("NONEXISTENT_TASK").email("x@x.com").mobile("111").build();

            assertThatThrownBy(() -> service.submitLead(1L, dto))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Invalid taskType");
        }

        @Test
        @DisplayName("DB config partnerUrl used when available")
        void dbConfigUrl_overridesDefault() {
            TaskConfig cfg = TaskConfig.builder()
                    .taskType(TaskType.LINK_ANGELONE)
                    .isActive(true)
                    .credits(200L)
                    .partnerUrl("https://custom.angelone.in/ref123")
                    .build();
            when(configRepo.findByTaskType(TaskType.LINK_ANGELONE)).thenReturn(Optional.of(cfg));
            when(leadRepo.findByUserIdAndTaskType(1L, TaskType.LINK_ANGELONE))
                    .thenReturn(Optional.empty());
            when(userRepo.findById(1L)).thenReturn(Optional.of(testUser));
            when(leadRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            TaskLeadSubmitDTO dto = TaskLeadSubmitDTO.builder()
                    .taskType("LINK_ANGELONE").email("u@e.com").mobile("9999").build();

            String url = service.submitLead(1L, dto);
            assertThat(url).isEqualTo("https://custom.angelone.in/ref123");
        }
    }

    // =========================================================================
    //  getTodayTasks
    // =========================================================================

    @Nested
    @DisplayName("getTodayTasks()")
    class GetTodayTasks {

        @Test
        @DisplayName("returns all active tasks sorted by category order")
        void returnsSortedTasks() {
            List<TaskDTO> tasks = service.getTodayTasks(1L);

            assertThat(tasks).isNotEmpty();
            // First task should be DAILY category (LOGIN, SPIN_WHEEL etc)
            assertThat(tasks.get(0).getCategory()).isEqualTo("DAILY");
        }

        @Test
        @DisplayName("completed task marked as completed=true")
        void completedTask_flaggedCorrectly() {
            DailyTaskCompletion loginCompletion = DailyTaskCompletion.builder()
                    .user(testUser).taskType(TaskType.LOGIN).creditsEarned(10L).build();
            when(taskRepo.findTodayCompletions(eq(1L), any(OffsetDateTime.class)))
                    .thenReturn(List.of(loginCompletion));

            List<TaskDTO> tasks = service.getTodayTasks(1L);

            TaskDTO loginTask = tasks.stream()
                    .filter(t -> t.getTaskType().equals("LOGIN"))
                    .findFirst().orElseThrow();
            assertThat(loginTask.isCompleted()).isTrue();
        }

        @Test
        @DisplayName("explicitly disabled task in DB config is excluded from list")
        void disabledTask_excluded() {
            TaskConfig disabledCfg = TaskConfig.builder()
                    .taskType(TaskType.SURVEY_COMPLETE).isActive(false).credits(100L).build();
            when(configRepo.findAll()).thenReturn(List.of(disabledCfg));

            List<TaskDTO> tasks = service.getTodayTasks(1L);

            boolean surveyPresent = tasks.stream()
                    .anyMatch(t -> t.getTaskType().equals("SURVEY_COMPLETE"));
            assertThat(surveyPresent).isFalse();
        }

        @Test
        @DisplayName("DB config label overrides fallback label")
        void dbConfigLabel_overridesFallback() {
            TaskConfig cfg = TaskConfig.builder()
                    .taskType(TaskType.LOGIN).isActive(true)
                    .credits(10L).label("Custom Login Label").build();
            when(configRepo.findAll()).thenReturn(List.of(cfg));

            List<TaskDTO> tasks = service.getTodayTasks(1L);

            TaskDTO loginTask = tasks.stream()
                    .filter(t -> t.getTaskType().equals("LOGIN"))
                    .findFirst().orElseThrow();
            assertThat(loginTask.getLabel()).isEqualTo("Custom Login Label");
        }
    }
}
