package com.myworld.modules.notification.application;

import com.myworld.core.dto.PageResponse;
import com.myworld.modules.notification.domain.Notification;

public interface NotificationService {
    // Existing Email/SMS
    void sendEmail(String to, String subject, String body);
    void sendSms(String phone, String message);
    void sendOtpEmail(String email, String otp);
    void sendOtpSms(String phone, String otp);
    void sendWelcomeEmail(String email, String name);
    void sendKycStatusEmail(String email, String name, String status, String reason);
    void sendPayoutStatusEmail(String email, String name, String status, String amount);

    // New In-App Notifications
    void sendNotification(Long userId, String type, String title, String message);
    PageResponse<Notification> getUserNotifications(Long userId, int page, int size);
    long getUnreadCount(Long userId);
    void markAsRead(Long notificationId, Long userId);
    void markAllAsRead(Long userId);
}