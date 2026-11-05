package com.red_escuela.backend.notification;

import com.red_escuela.backend.billing.events.GatewayPaymentArrivedWithGatewayDisabledEvent;
import com.red_escuela.backend.notification.entity.NotificationType;
import com.red_escuela.backend.shared.event.ProcessedEffectGuard;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class GatewayPaymentArrivedWithGatewayDisabledNotificationListener {
    static final String SECRETARIAT_HANDLER = "GatewayPaymentArrivedWithGatewayDisabledNotificationListener.notifySecretariat";

    private final ProcessedEffectGuard effectGuard;
    private final ApplicationEventPublisher eventPublisher;

    public GatewayPaymentArrivedWithGatewayDisabledNotificationListener(ProcessedEffectGuard effectGuard, ApplicationEventPublisher eventPublisher) {
        this.effectGuard = effectGuard;
        this.eventPublisher = eventPublisher;
    }

    @ApplicationModuleListener
    public void notifySecretariat(GatewayPaymentArrivedWithGatewayDisabledEvent event) {
        effectGuard.runEffectOnce(event, SECRETARIAT_HANDLER, () -> eventPublisher.publishEvent(new BillingOperationNotification(
                NotificationType.GATEWAY_PAYMENT_WITH_GATEWAY_OFF,
                "Entró un pago de " + event.currency() + " " + event.amount() + " por " + gatewayLabelOf(event.gateway())
                        + " con la pasarela apagada" + invoiceSuffixOf(event.reportedInvoiceId()) + ". Quedó acreditado en la cuenta del alumno.",
                List.of(NotificationTopic.ENROLLMENT_SECRETARIAT), "Payment", event.paymentId())));
    }

    private static String gatewayLabelOf(String gateway) {
        return "MERCADO_PAGO".equals(gateway) ? "Mercado Pago" : gateway;
    }

    private static String invoiceSuffixOf(Long reportedInvoiceId) {
        return reportedInvoiceId == null ? "" : " (factura " + reportedInvoiceId + ")";
    }
}
