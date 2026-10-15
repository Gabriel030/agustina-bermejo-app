package com.red_escuela.backend.notification;

import com.red_escuela.backend.billing.events.LateFeeCorrectionIssuedAfterFiscalWindowEvent;
import com.red_escuela.backend.notification.entity.NotificationType;
import com.red_escuela.backend.shared.event.ProcessedEffectGuard;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.List;

@Component
public class LateFeeCorrectionNotificationListener {
    private static final DateTimeFormatter DAY_MONTH_YEAR = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    static final String SECRETARIAT_HANDLER = "LateFeeCorrectionNotificationListener.notifySecretariatWhenIssuedAfterFiscalWindow";

    private final ProcessedEffectGuard effectGuard;
    private final ApplicationEventPublisher eventPublisher;

    public LateFeeCorrectionNotificationListener(ProcessedEffectGuard effectGuard, ApplicationEventPublisher eventPublisher) {
        this.effectGuard = effectGuard;
        this.eventPublisher = eventPublisher;
    }

    @ApplicationModuleListener
    public void notifySecretariatWhenIssuedAfterFiscalWindow(LateFeeCorrectionIssuedAfterFiscalWindowEvent event) {
        effectGuard.runEffectOnce(event, SECRETARIAT_HANDLER, () -> eventPublisher.publishEvent(new BillingOperationNotification(
                NotificationType.LATE_FEE_CORRECTION_AFTER_FISCAL_WINDOW,
                "La nota de crédito " + event.creditNoteNumber() + " (corrección del recargo de la nota de débito " + event.debitNoteNumber()
                        + ") se emitió el " + DAY_MONTH_YEAR.format(event.issueDate()) + ", y la baja se registró el "
                        + DAY_MONTH_YEAR.format(event.withdrawalRegisteredOn()) + ": fuera de los 15 días de la RG 4540. Revisala con el contador.",
                List.of(NotificationTopic.ENROLLMENT_SECRETARIAT), "Invoice", event.debitNoteId())));
    }
}
