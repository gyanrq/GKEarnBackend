package com.myworld.modules.notification.api;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class NotificationEvent {
    private final Long userId;
    private final String type;
    private final String title;
    private final String message;
}