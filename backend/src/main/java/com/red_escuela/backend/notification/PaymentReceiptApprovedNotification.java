package com.red_escuela.backend.notification;

import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.notification.entity.NotificationType;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;

public record PaymentReceiptApprovedNotification(Long invoiceId, YearMonth invoicePeriod, Person payer) implements DomainNotificationEvent {
    private static final DateTimeFormatter PERIOD_FORMAT = DateTimeFormatter.ofPattern("MM/yyyy");

    @Override
    public NotificationType type() {
        return NotificationType.PAYMENT_RECEIPT_APPROVED;
    }

    @Override
    public String message() {
        return "Aprobamos tu comprobante: el pago de " + invoicePeriod.format(PERIOD_FORMAT) + " ya está acreditado.";
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
