package com.red_escuela.backend.billing.events;

import com.red_escuela.backend.billing.entity.BankTransferReceipt;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.shared.event.CorrelationIds;
import com.red_escuela.backend.shared.event.DomainEvent;
import com.red_escuela.backend.shared.time.ApplicationTime;

import java.time.Instant;
import java.time.YearMonth;

public record PaymentReceiptApprovedEvent(String correlationId, Instant occurredAt, Long invoiceId, YearMonth invoicePeriod, Long payerPersonId) implements DomainEvent {

    public static PaymentReceiptApprovedEvent approvedFor(BankTransferReceipt receipt, Person payer) {
        return new PaymentReceiptApprovedEvent(CorrelationIds.current(), ApplicationTime.instant(), receipt.getInvoice().getId(),
                receipt.getInvoice().getPeriod(), payer == null ? null : payer.getId());
    }
}
