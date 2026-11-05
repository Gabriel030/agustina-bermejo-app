package com.red_escuela.backend.notification;

import com.red_escuela.backend.billing.events.GatewayPaymentArrivedWithGatewayDisabledEvent;
import com.red_escuela.backend.shared.event.ProcessedEffectGuard;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("D-256")
class GatewayPaymentArrivedWithGatewayDisabledNotificationListenerTest {

    private final ProcessedEffectGuard guard = mock(ProcessedEffectGuard.class);
    private final ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
    private final GatewayPaymentArrivedWithGatewayDisabledNotificationListener listener =
            new GatewayPaymentArrivedWithGatewayDisabledNotificationListener(guard, publisher);

    private String messageOf(GatewayPaymentArrivedWithGatewayDisabledEvent event) {
        when(guard.runEffectOnce(any(), anyString(), any())).thenAnswer(call -> {
            ((Runnable) call.getArgument(2)).run();
            return true;
        });
        listener.notifySecretariat(event);
        ArgumentCaptor<BillingOperationNotification> captured = ArgumentCaptor.forClass(BillingOperationNotification.class);
        verify(publisher).publishEvent(captured.capture());
        return captured.getValue().message();
    }

    @Test
    void theMessageNamesMercadoPagoAndTheInvoice() {
        String message = messageOf(GatewayPaymentArrivedWithGatewayDisabledEvent.arrivedFor(7L, 55L, "MERCADO_PAGO", new BigDecimal("5000.00"), "ARS"));

        assertThat(message).contains("Mercado Pago").contains("(factura 55)").doesNotContain("MERCADO_PAGO").doesNotContain("null");
    }

    @Test
    void theMessageHasNoInvoiceSuffixWhenThereIsNoReportedInvoice() {
        String message = messageOf(GatewayPaymentArrivedWithGatewayDisabledEvent.arrivedFor(7L, null, "MERCADO_PAGO", new BigDecimal("5000.00"), "ARS"));

        assertThat(message).doesNotContain("factura").doesNotContain("null");
    }
}
