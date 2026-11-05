package com.red_escuela.backend.notification;

import com.red_escuela.backend.academicstructure.events.HolidaysMissingForNextYearEvent;
import com.red_escuela.backend.notification.entity.NotificationType;
import com.red_escuela.backend.shared.event.ProcessedEffectGuard;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class HolidaysMissingNotificationListener {
    static final String NOTIFY_ADMINISTRATIVE_HANDLER = "HolidaysMissingNotificationListener.notifyAdministrative";
    static final String HOLIDAY_ENTITY_TYPE = "Holiday";

    private final ProcessedEffectGuard effectGuard;
    private final ApplicationEventPublisher eventPublisher;

    public HolidaysMissingNotificationListener(ProcessedEffectGuard effectGuard, ApplicationEventPublisher eventPublisher) {
        this.effectGuard = effectGuard;
        this.eventPublisher = eventPublisher;
    }

    @ApplicationModuleListener
    public void notifyAdministrative(HolidaysMissingForNextYearEvent event) {
        effectGuard.runEffectOnce(event, NOTIFY_ADMINISTRATIVE_HANDLER, () -> eventPublisher.publishEvent(new BillingOperationNotification(
                NotificationType.HOLIDAYS_MISSING_FOR_NEXT_YEAR,
                "Todavía no cargaste los feriados de " + event.year() + ": sin ellos no se pueden crear períodos de ese año. Cargalos desde Feriados.",
                List.of(NotificationTopic.HOLIDAY_CALENDAR_ADMINISTRATION), HOLIDAY_ENTITY_TYPE, null)));
    }
}
