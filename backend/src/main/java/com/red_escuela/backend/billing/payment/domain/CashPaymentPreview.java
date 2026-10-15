package com.red_escuela.backend.billing.payment.domain;

import com.red_escuela.backend.shared.domain.NonNegativeMoney;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

public record CashPaymentPreview(List<CoveredInvoice> coveredInvoices, List<CoveredInvoice> coveredByRemainingCredit, NonNegativeMoney unallocatedAfterwards,
                                 String fingerprint) {

    public CashPaymentPreview {
        coveredInvoices = List.copyOf(coveredInvoices);
        coveredByRemainingCredit = List.copyOf(coveredByRemainingCredit);
        Objects.requireNonNull(unallocatedAfterwards, "unallocatedAfterwards");
        Objects.requireNonNull(fingerprint, "fingerprint");
    }

    public record CoveredInvoice(Long invoiceId, String concept, LocalDate dueDate, NonNegativeMoney amount) {

        public CoveredInvoice {
            Objects.requireNonNull(invoiceId, "invoiceId");
            Objects.requireNonNull(amount, "amount");
        }
    }
}
