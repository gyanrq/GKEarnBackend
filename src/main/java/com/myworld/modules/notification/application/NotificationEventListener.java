package com.myworld.modules.notification.application;

import com.myworld.modules.notification.api.NotificationEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventListener {

    private final NotificationService notificationService;

    // Trigger async only AFTER the main transaction successfully commits.
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleNotificationEvent(NotificationEvent event) {
        log.info("Processing async notification event for userId={}", event.getUserId());
        notificationService.sendNotification(
                event.getUserId(), 
                event.getType(), 
                event.getTitle(), 
                event.getMessage()
        );
    }
}