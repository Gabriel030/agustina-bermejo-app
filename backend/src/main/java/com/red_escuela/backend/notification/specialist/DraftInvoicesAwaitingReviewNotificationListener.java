package com.red_escuela.backend.notification.specialist;

import com.red_escuela.backend.billing.events.DraftInvoicesAwaitingReviewEvent;
import com.red_escuela.backend.notification.domain.BillingOperationNotification;
import com.red_escuela.backend.notification.domain.NotificationTopic;
import com.red_escuela.backend.notification.entity.NotificationType;
import com.red_escuela.backend.shared.event.ProcessedEffectGuard;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import java.time.YearMonth;
import java.util.List;

@Component
public class DraftInvoicesAwaitingReviewNotificationListener {
    static final String STAFF_HANDLER = "DraftInvoicesAwaitingReviewNotificationListener.notifyStaff";
    static final String RUN_ENTITY_TYPE = "MonthlyInvoiceRun";

    private final ProcessedEffectGuard effectGuard;
    private final ApplicationEventPublisher eventPublisher;

    public DraftInvoicesAwaitingReviewNotificationListener(ProcessedEffectGuard effectGuard, ApplicationEventPublisher eventPublisher) {
        this.effectGuard = effectGuard;
        this.eventPublisher = eventPublisher;
    }

    @ApplicationModuleListener
    public void notifyStaff(DraftInvoicesAwaitingReviewEvent event) {
        effectGuard.runEffectOnce(event, STAFF_HANDLER, () -> eventPublisher.publishEvent(new BillingOperationNotification(
                NotificationType.DRAFT_INVOICES_AWAITING_REVIEW,
                "Quedan " + event.pendingDrafts() + " borradores de la emisión de " + YearMonth.from(event.period()) + " sin aprobar: revisalos y aprobalos desde Corridas.",
                List.of(NotificationTopic.BILLING_RUN_SUPERVISION), RUN_ENTITY_TYPE, event.monthlyInvoiceRunId())));
    }
}
