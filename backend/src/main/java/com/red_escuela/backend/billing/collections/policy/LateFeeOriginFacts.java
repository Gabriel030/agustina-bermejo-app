package com.red_escuela.backend.billing.collections.policy;

import com.red_escuela.backend.billing.invoice.domain.InvoiceDocumentType;
import com.red_escuela.backend.billing.invoice.domain.InvoicePaymentStatus;
import com.red_escuela.backend.billing.invoice.domain.InvoiceType;
import com.red_escuela.backend.billing.invoice.domain.StudentSnapshot;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Objects;

public record LateFeeOriginFacts(Long invoiceId, String invoiceNumber, Long studentCurrentAccountId, InvoiceDocumentType documentType, InvoiceType invoiceType,
                                 InvoicePaymentStatus paymentStatus, LocalDate dueDate, Currency currency, NonNegativeMoney finalLinePrice,
                                 StudentSnapshot recipient, YearMonth period, Long programEnrollmentId, Long courseEnrollmentId) {

    public LateFeeOriginFacts {
        Objects.requireNonNull(invoiceId, "invoiceId");
        Objects.requireNonNull(studentCurrentAccountId, "studentCurrentAccountId");
        Objects.requireNonNull(documentType, "documentType");
        Objects.requireNonNull(invoiceType, "invoiceType");
        Objects.requireNonNull(paymentStatus, "paymentStatus");
        Objects.requireNonNull(dueDate, "dueDate");
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(finalLinePrice, "finalLinePrice");
        Objects.requireNonNull(recipient, "recipient");
        Objects.requireNonNull(period, "period");
    }
}
