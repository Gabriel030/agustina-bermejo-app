package com.red_escuela.backend.notification;

import com.red_escuela.backend.billing.events.MonthlyInvoiceItemRetriesExhaustedEvent;
import com.red_escuela.backend.billing.events.MonthlyInvoiceRunMissedEvent;
import com.red_escuela.backend.notification.entity.NotificationType;
import com.red_escuela.backend.shared.event.ProcessedEffectGuard;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import java.time.YearMonth;
import java.util.List;

@Component
public class MonthlyInvoiceRunAlertNotificationListener {
    static final String MISSED_HANDLER = "MonthlyInvoiceRunAlertNotificationListener.notifyRunMissed";
    static final String RETRIES_EXHAUSTED_HANDLER = "MonthlyInvoiceRunAlertNotificationListener.notifyItemRetriesExhausted";
    static final String RUN_ENTITY_TYPE = "MonthlyInvoiceRun";
    static final String ITEM_ENTITY_TYPE = "MonthlyInvoiceRunItem";

    private final ProcessedEffectGuard effectGuard;
    private final ApplicationEventPublisher eventPublisher;

    public MonthlyInvoiceRunAlertNotificationListener(ProcessedEffectGuard effectGuard, ApplicationEventPublisher eventPublisher) {
        this.effectGuard = effectGuard;
        this.eventPublisher = eventPublisher;
    }

    @ApplicationModuleListener
    public void notifyRunMissed(MonthlyInvoiceRunMissedEvent event) {
        effectGuard.runEffectOnce(event, MISSED_HANDLER, () -> eventPublisher.publishEvent(new BillingOperationNotification(
                NotificationType.MONTHLY_INVOICE_RUN_MISSED,
                "La emisión mensual de " + YearMonth.from(event.period()) + " no corrió o falló: simulá y emití a mano desde Corridas.",
                List.of(NotificationTopic.BILLING_RUN_SUPERVISION), RUN_ENTITY_TYPE, null)));
    }

    @ApplicationModuleListener
    public void notifyItemRetriesExhausted(MonthlyInvoiceItemRetriesExhaustedEvent event) {
        effectGuard.runEffectOnce(event, RETRIES_EXHAUSTED_HANDLER, () -> eventPublisher.publishEvent(new BillingOperationNotification(
                NotificationType.MONTHLY_INVOICE_ITEM_RETRIES_EXHAUSTED,
                "Se agotaron los reintentos de emisión de la inscripción " + event.enrollmentType() + " " + event.enrollmentId() + " (causa: " + event.errorCode() + "): revisala y reintentá a mano.",
                List.of(NotificationTopic.BILLING_RUN_SUPERVISION), ITEM_ENTITY_TYPE, event.monthlyInvoiceRunItemId())));
    }
}
