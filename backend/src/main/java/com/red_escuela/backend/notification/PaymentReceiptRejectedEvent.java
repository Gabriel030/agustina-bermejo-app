package com.red_escuela.backend.notification;

import com.red_escuela.backend.billing.entity.PaymentReceipt;
import com.red_escuela.backend.notification.entity.NotificationType;

import java.time.format.DateTimeFormatter;
import java.util.List;

public record PaymentReceiptRejectedEvent(PaymentReceipt receipt) implements DomainNotificationEvent {
    private static final DateTimeFormatter PERIOD_FORMAT = DateTimeFormatter.ofPattern("MM/yyyy");

    @Override
    public NotificationType type() {
        return NotificationType.PAYMENT_RECEIPT_REJECTED;
    }

    @Override
    public String message() {
        return "Rechazamos el comprobante que subiste para el pago de " + receipt.getInvoice().getPeriod().format(PERIOD_FORMAT)
                + ": " + receipt.getRejectionReason() + ". Podés subir uno nuevo desde Mis pagos.";
    }

    @Override
    public List<NotificationRecipient> recipients() {
        return receipt.payer() == null ? List.of() : List.of(new PersonRecipient(receipt.payer()));
    }

    @Override
    public String relatedEntityType() {
        return "Invoice";
    }

    @Override
    public Long relatedEntityId() {
        return receipt.getInvoice().getId();
    }
}
