package com.myworld.modules.notification.application;

import com.myworld.core.dto.PageResponse;
import com.myworld.core.exception.ResourceNotFoundException;
import com.myworld.modules.identity.domain.User;
import com.myworld.modules.identity.infrastructure.UserRepository;
import com.myworld.modules.notification.domain.Notification;
import com.myworld.modules.notification.infrastructure.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepo;
    private final UserRepository userRepo;
    private final JavaMailSender mailSender;

    // ── SMS CONFIG ─────────────────────────────────────────────
    @Value("${sms.provider.enabled:false}")
    private boolean smsEnabled;

    @Value("${sms.msg91.auth-key:}")
    private String msg91AuthKey;

    @Value("${sms.msg91.template-id:}")
    private String msg91TemplateId;

    @Value("${sms.msg91.sender-id:EARNXX}")
    private String msg91SenderId;

    private final RestTemplate restTemplate = new RestTemplate();

    // ── EMAIL ─────────────────────────────────────────────

    // FIX: @Async added — sendEmail() was synchronous so any SMTP timeout
    // (up to 30s) would block the calling thread. This affects OTP delivery during
    // login/register and admin password-reset emails. @Async runs on Spring's
    // task executor thread pool so the caller returns immediately.
    @org.springframework.retry.annotation.Retryable(retryFor = {org.springframework.mail.MailException.class}, maxAttempts = 3, backoff = @org.springframework.retry.annotation.Backoff(delay = 5000))
    @Async
    @Override
    public void sendEmail(String to, String subject, String body) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");

            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(body, false);

            mailSender.send(message);

            log.info("[EMAIL] Sent to={} subject={}", to, subject);

        } catch (MessagingException e) {
            log.error("[EMAIL] MessagingException: {}", e.getMessage(), e);
        } catch (Exception e) {
            log.error("[EMAIL] Failed: {}", e.getMessage(), e);
        }
    }

    @Override
    public void sendOtpEmail(String email, String otp) {
        sendEmail(email, "Your EarnX3 OTP Code",
                """
                Hi,

                Your OTP is: %s

                Valid for 5 minutes.

                — EarnX3 Team
                """.formatted(otp));
    }

    @Override
    public void sendWelcomeEmail(String email, String name) {
        sendEmail(email, "Welcome to EarnX3!",
                """
                Hi %s,

                Welcome to EarnX3!

                — EarnX3 Team
                """.formatted(name));
    }

    @Override
    public void sendKycStatusEmail(String email, String name, String status, String reason) {
        String reasonText = (reason != null && !reason.isBlank()) ? "\nReason: " + reason : "";

        sendEmail(email, "KYC Status: " + status,
                """
                Hi %s,

                Status: %s%s

                — EarnX3 Team
                """.formatted(name, status, reasonText));
    }

    @Override
    public void sendPayoutStatusEmail(String email, String name, String status, String amount) {
        sendEmail(email, "Payout " + status,
                """
                Hi %s,

                Amount ₹%s is %s

                — EarnX3 Team
                """.formatted(name, amount, status));
    }

    // ── SMS ─────────────────────────────────────────────

    @Override
    public void sendSms(String phone, String message) {

        if (!smsEnabled) {
            log.info("[SMS MOCK] {} -> {}", phone, message);
            return;
        }

        if (msg91AuthKey == null || msg91AuthKey.isBlank()) {
            log.error("[SMS] Auth key missing");
            return;
        }

        try {
            String mobile = normalisePhone(phone);
            Map<String, Object> body = Map.of(
                    "template_id", msg91TemplateId,
                    "sender", msg91SenderId,
                    "mobiles", mobile,
                    "VAR1", message
            );

            org.springframework.web.reactive.function.client.WebClient webClient = org.springframework.web.reactive.function.client.WebClient.builder().baseUrl("https://api.msg91.com").build();
            webClient.post().uri("/api/v5/flow/")
                .header("authkey", msg91AuthKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .subscribe(
                    resp -> log.info("[SMS] Sent: {}", resp),
                    err  -> log.error("[SMS] Failed: {}", err.getMessage())
                );
        } catch (Exception e) {
            log.error("[SMS ERROR] {}", e.getMessage(), e);
        }
    }

    @Override
    public void sendOtpSms(String phone, String otp) {
        sendSms(phone, "OTP: " + otp);
    }

    // ── NOTIFICATIONS ─────────────────────────────────────────────

    @Override
    @Transactional
    public void sendNotification(Long userId, String type, String title, String message) {

        User user = userRepo.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (message != null && message.length() > 500) {
            message = message.substring(0, 497) + "...";
        }

        Notification notification = Notification.builder()
                .user(user)
                .type(type)
                .title(title)
                .message(message)
                .read(false)
                .build();

        notificationRepo.save(notification);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<Notification> getUserNotifications(Long userId, int page, int size) {

        Page<Notification> pg =
                notificationRepo.findByUserIdOrderByCreatedAtDesc(
                        userId, PageRequest.of(page, size));

        return PageResponse.<Notification>builder()
                .content(pg.getContent())
                .page(pg.getNumber())
                .size(pg.getSize())
                .totalElements(pg.getTotalElements())
                .totalPages(pg.getTotalPages())
                .last(pg.isLast())
                .build();
    }

    @Override
    public long getUnreadCount(Long userId) {
        return notificationRepo.countByUserIdAndReadFalse(userId);
    }

    @Override
    public void markAsRead(Long notificationId, Long userId) {

        Notification notif = notificationRepo.findByIdAndUserId(notificationId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Not found"));
        notif.setRead(true);
        notificationRepo.save(notif);
    }

    @Override
    public void markAllAsRead(Long userId) {
        notificationRepo.markAllAsReadByUserId(userId);
    }

    // ── HELPER ─────────────────────────────────────────────

    private String normalisePhone(String phone) {

        if (phone == null) return "";

        String digits = phone.replaceAll("[^0-9]", "");

        if (digits.length() == 10) return "91" + digits;

        return digits;
    }
}
