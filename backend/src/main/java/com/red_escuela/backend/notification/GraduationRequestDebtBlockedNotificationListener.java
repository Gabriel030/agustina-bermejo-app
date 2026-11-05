package com.red_escuela.backend.notification;

import com.red_escuela.backend.graduation.events.GraduationRequestDebtBlockedEvent;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.notification.entity.Notification;
import com.red_escuela.backend.notification.entity.NotificationType;
import com.red_escuela.backend.notification.repository.NotificationRepository;
import com.red_escuela.backend.shared.event.ProcessedEffectGuard;
import com.red_escuela.backend.shared.module.BillingMode;
import com.red_escuela.backend.shared.module.CurrentBillingModePort;
import com.red_escuela.backend.shared.time.ApplicationTime;
import jakarta.persistence.EntityManager;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;

@Component
public class GraduationRequestDebtBlockedNotificationListener {
    static final String NOTIFY_DEBT_BLOCKED_HANDLER = "GraduationRequestDebtBlockedNotificationListener.notifyDebtBlocked";
    static final Duration RESEND_AFTER = Duration.ofDays(1);

    private final ProcessedEffectGuard effectGuard;
    private final ApplicationEventPublisher eventPublisher;
    private final EntityManager entityManager;
    private final NotificationRepository notificationRepository;
    private final CurrentBillingModePort billingMode;

    public GraduationRequestDebtBlockedNotificationListener(ProcessedEffectGuard effectGuard, ApplicationEventPublisher eventPublisher,
                                                            EntityManager entityManager, NotificationRepository notificationRepository,
                                                            CurrentBillingModePort billingMode) {
        this.effectGuard = effectGuard;
        this.eventPublisher = eventPublisher;
        this.entityManager = entityManager;
        this.notificationRepository = notificationRepository;
        this.billingMode = billingMode;
    }

    @ApplicationModuleListener
    public void notifyDebtBlocked(GraduationRequestDebtBlockedEvent event) {
        effectGuard.runEffectOnce(DerivedEventIdentity.of(event), NOTIFY_DEBT_BLOCKED_HANDLER, () -> {
            GraduationRequestDebtBlockedNotification notification = new GraduationRequestDebtBlockedNotification(event.requestId(),
                    entityManager.getReference(Person.class, event.studentPersonId()), event.totalDue(), event.installmentCount(),
                    billingMode.currentMode() == BillingMode.FULL);
            if (shouldNotify(notification)) {
                eventPublisher.publishEvent(notification);
            }
        });
    }

    private boolean shouldNotify(GraduationRequestDebtBlockedNotification notification) {
        return notificationRepository.findFirstByTypeAndRelatedEntityTypeAndRelatedEntityIdOrderByCreatedAtDesc(
                        NotificationType.GRADUATION_REQUEST_DEBT_BLOCKED, notification.relatedEntityType(), notification.relatedEntityId())
                .map(last -> messageChangedSince(last, notification) || resendWindowElapsed(last))
                .orElse(true);
    }

    private boolean messageChangedSince(Notification last, GraduationRequestDebtBlockedNotification notification) {
        return !last.getMessage().equals(InAppNotificationChannel.abbreviateToColumnLength(notification.message()));
    }

    private boolean resendWindowElapsed(Notification last) {
        LocalDateTime threshold = ApplicationTime.now().minus(RESEND_AFTER);
        return !last.getCreatedAt().isAfter(threshold);
    }
}
