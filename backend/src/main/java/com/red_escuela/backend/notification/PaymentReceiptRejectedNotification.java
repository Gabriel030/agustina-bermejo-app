package com.red_escuela.backend.notification;

import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.notification.entity.NotificationType;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;

public record PaymentReceiptRejectedNotification(Long invoiceId, YearMonth invoicePeriod, String rejectionReason, Person payer) implements DomainNotificationEvent {
    private static final DateTimeFormatter PERIOD_FORMAT = DateTimeFormatter.ofPattern("MM/yyyy");

    @Override
    public NotificationType type() {
        return NotificationType.PAYMENT_RECEIPT_REJECTED;
    }

    @Override
    public String message() {
        return "Rechazamos el comprobante que subiste para el pago de " + invoicePeriod.format(PERIOD_FORMAT)
                + ": " + rejectionReason + ". Podés subir uno nuevo desde Mis pagos.";
    }

    @Override
    public List<NotificationRecipient> recipients() {
        return payer == null ? List.of() : List.of(new PersonRecipient(payer));
    }

    @Override
    public String relatedEntityType() {
        return "Invoice";
    }

    @Override
    public Long relatedEntityId() {
        return invoiceId;
    }
}
