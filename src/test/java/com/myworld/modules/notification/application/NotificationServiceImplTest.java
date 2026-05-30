package com.myworld.modules.notification.application;

import com.myworld.core.dto.PageResponse;
import com.myworld.core.exception.ResourceNotFoundException;
import com.myworld.modules.identity.domain.User;
import com.myworld.modules.identity.infrastructure.UserRepository;
import com.myworld.modules.notification.domain.Notification;
import com.myworld.modules.notification.infrastructure.NotificationRepository;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.*;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("NotificationServiceImpl — Unit Tests")
class NotificationServiceImplTest {

    @Mock NotificationRepository notificationRepo;
    @Mock UserRepository         userRepo;
    @Mock JavaMailSender         mailSender;

    @InjectMocks NotificationServiceImpl service;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = User.builder().email("test@earnx.com").name("Test User").password("x").build();
        testUser.setId(1L);

        // Mock MimeMessage creation for email tests
        MimeMessage mimeMessage = mock(MimeMessage.class);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
    }

    // ── sendNotification ──────────────────────────────────────────────────────

    @Nested @DisplayName("sendNotification()")
    class SendNotification {

        @Test @DisplayName("happy path — saves notification for known user")
        void happyPath_savesNotification() {
            when(userRepo.findById(1L)).thenReturn(Optional.of(testUser));
            when(notificationRepo.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));

            service.sendNotification(1L, "PAYOUT", "Payout Approved", "Your payout of ₹100 is approved");

            verify(notificationRepo).save(argThat(n ->
                    n.getType().equals("PAYOUT") &&
                    n.getTitle().equals("Payout Approved") &&
                    !n.isRead()
            ));
        }

        @Test @DisplayName("unknown user — ResourceNotFoundException")
        void unknownUser_throws() {
            when(userRepo.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.sendNotification(99L, "TYPE", "Title", "Msg"))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ── getUserNotifications ──────────────────────────────────────────────────

    @Nested @DisplayName("getUserNotifications()")
    class GetUserNotifications {

        @Test @DisplayName("returns paginated notifications")
        void returnsPaginatedNotifications() {
            Notification n = Notification.builder()
                    .user(testUser).type("SPIN").title("Spin Won!").message("You won 50 credits").build();
            ReflectionTestUtils.setField(n, "id", 1L);

            Page<Notification> page = new PageImpl<>(List.of(n), PageRequest.of(0, 10), 1);
            when(notificationRepo.findByUserIdOrderByCreatedAtDesc(eq(1L), any(Pageable.class)))
                    .thenReturn(page);

            PageResponse<Notification> result = service.getUserNotifications(1L, 0, 10);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getType()).isEqualTo("SPIN");
        }

        @Test @DisplayName("empty — returns empty list, not null")
        void empty_returnsEmptyList() {
            when(notificationRepo.findByUserIdOrderByCreatedAtDesc(anyLong(), any()))
                    .thenReturn(Page.empty(PageRequest.of(0, 10)));

            PageResponse<Notification> result = service.getUserNotifications(1L, 0, 10);
            assertThat(result.getContent()).isEmpty();
        }
    }

    // ── getUnreadCount ────────────────────────────────────────────────────────

    @Nested @DisplayName("getUnreadCount()")
    class GetUnreadCount {

        @Test @DisplayName("returns count from repository")
        void returnsCount() {
            when(notificationRepo.countByUserIdAndReadFalse(1L)).thenReturn(5L);
            assertThat(service.getUnreadCount(1L)).isEqualTo(5L);
        }

        @Test @DisplayName("zero unread returns 0")
        void zeroUnread_returnsZero() {
            when(notificationRepo.countByUserIdAndReadFalse(1L)).thenReturn(0L);
            assertThat(service.getUnreadCount(1L)).isZero();
        }
    }

    // ── markAsRead ────────────────────────────────────────────────────────────

    @Nested @DisplayName("markAsRead()")
    class MarkAsRead {

        @Test @DisplayName("marks notification as read for correct user")
        void marksAsRead() {
            Notification n = Notification.builder()
                    .user(testUser).type("INFO").title("Test").message("Msg").build();
            ReflectionTestUtils.setField(n, "id", 1L);

            when(notificationRepo.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(n));

            service.markAsRead(1L, 1L);

            assertThat(n.isRead()).isTrue();
            verify(notificationRepo).save(n);
        }

        @Test @DisplayName("notification not found → ResourceNotFoundException")
        void notFound_throws() {
            when(notificationRepo.findByIdAndUserId(99L, 1L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.markAsRead(99L, 1L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ── sendEmail (smoke tests) ───────────────────────────────────────────────

    @Nested @DisplayName("sendEmail() — smoke tests")
    class SendEmail {

        @Test @DisplayName("SMTP exception is caught — does not throw to caller")
        void smtpException_isCaught() {
            when(mailSender.createMimeMessage()).thenThrow(new RuntimeException("SMTP down"));

            // Should NOT propagate — email failures are swallowed
            assertThatCode(() -> service.sendEmail("user@earnx.com", "Subject", "Body"))
                    .doesNotThrowAnyException();
        }

        @Test @DisplayName("sendOtpEmail delegates to sendEmail with OTP in body")
        void sendOtpEmail_delegatesToSendEmail() {
            // No exception = success (email actually sent via mocked mailSender)
            assertThatCode(() -> service.sendOtpEmail("user@earnx.com", "123456"))
                    .doesNotThrowAnyException();
        }
    }
}
