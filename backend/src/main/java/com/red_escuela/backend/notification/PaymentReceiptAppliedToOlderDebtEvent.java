package com.red_escuela.backend.notification;

import com.red_escuela.backend.billing.entity.BankTransferReceipt;
import com.red_escuela.backend.notification.entity.NotificationType;

import java.time.format.DateTimeFormatter;
import java.util.List;

public record PaymentReceiptAppliedToOlderDebtEvent(BankTransferReceipt receipt) implements DomainNotificationEvent {
    private static final DateTimeFormatter PERIOD_FORMAT = DateTimeFormatter.ofPattern("MM/yyyy");

    @Override
    public NotificationType type() {
        return NotificationType.PAYMENT_RECEIPT_APPLIED_TO_OLDER_DEBT;
    }

    @Override
    public String message() {
        return "Aprobamos tu comprobante, pero tenías una deuda más antigua y el pago se aplicó primero a esa deuda. La factura de "
                + receipt.getInvoice().getPeriod().format(PERIOD_FORMAT) + " sigue pendiente: subí un nuevo comprobante para pagarla.";
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
