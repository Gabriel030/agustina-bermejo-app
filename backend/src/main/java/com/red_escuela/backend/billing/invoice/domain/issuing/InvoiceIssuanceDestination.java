package com.red_escuela.backend.billing.invoice.domain.issuing;

import java.util.Objects;

public sealed interface InvoiceIssuanceDestination {

    InvoiceIssuanceDestination EMIT_NOW = new EmitNow();

    record EmitNow() implements InvoiceIssuanceDestination {
    }

    record DraftOfMonthlyRun(Long monthlyInvoiceRunId) implements InvoiceIssuanceDestination {
        public DraftOfMonthlyRun {
            Objects.requireNonNull(monthlyInvoiceRunId, "monthlyInvoiceRunId");
        }
    }
}
