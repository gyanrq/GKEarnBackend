package com.myworld.modules.notification.web;

import com.myworld.core.dto.ApiResponse;
import com.myworld.core.dto.PageResponse;
import com.myworld.core.security.CustomUserDetails;
import com.myworld.modules.notification.application.NotificationService;
import com.myworld.modules.notification.domain.Notification;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public ApiResponse<PageResponse<Notification>> getNotifications(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        return ApiResponse.success(notificationService.getUserNotifications(userDetails.getId(), page, size));
    }

    @GetMapping("/unread-count")
    public ApiResponse<Long> getUnreadCount(@AuthenticationPrincipal CustomUserDetails userDetails) {
        return ApiResponse.success(notificationService.getUnreadCount(userDetails.getId()));
    }

    @PostMapping("/{id}/read")
    public ApiResponse<String> markAsRead(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        
        notificationService.markAsRead(id, userDetails.getId());
        return ApiResponse.success("Marked as read");
    }

    @PostMapping("/mark-all-read")
    public ApiResponse<String> markAllAsRead(@AuthenticationPrincipal CustomUserDetails userDetails) {
        notificationService.markAllAsRead(userDetails.getId());
        return ApiResponse.success("All notifications marked as read");
    }
}