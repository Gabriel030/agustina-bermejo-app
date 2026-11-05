package com.red_escuela.backend.notification;

import com.red_escuela.backend.billing.events.PaymentReceiptReviewOverdueEvent;
import com.red_escuela.backend.notification.entity.NotificationType;
import com.red_escuela.backend.shared.event.ProcessedEffectGuard;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PaymentReceiptReviewOverdueNotificationListener {
    static final String DIRECTOR_HANDLER = "PaymentReceiptReviewOverdueNotificationListener.notifyDirector";

    private final ProcessedEffectGuard effectGuard;
    private final ApplicationEventPublisher eventPublisher;

    public PaymentReceiptReviewOverdueNotificationListener(ProcessedEffectGuard effectGuard, ApplicationEventPublisher eventPublisher) {
        this.effectGuard = effectGuard;
        this.eventPublisher = eventPublisher;
    }

    @ApplicationModuleListener
    public void notifyDirector(PaymentReceiptReviewOverdueEvent event) {
        effectGuard.runEffectOnce(event, DIRECTOR_HANDLER, () -> eventPublisher.publishEvent(new BillingOperationNotification(
                NotificationType.PAYMENT_RECEIPT_REVIEW_OVERDUE,
                "Hay un comprobante de transferencia que lleva " + event.pendingBusinessDays() + " días hábiles sin decidirse (subido el " + event.uploadedOn()
                        + "). La factura sigue protegida mientras tanto. Revisalo en la bandeja de comprobantes.",
                List.of(NotificationTopic.DIRECTOR_APPROVALS), "BankTransferReceipt", event.receiptId())));
    }
}
