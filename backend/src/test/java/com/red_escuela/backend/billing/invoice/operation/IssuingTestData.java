package com.red_escuela.backend.billing.invoice.operation;

import com.red_escuela.backend.billing.invoice.domain.DiscountType;
import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.billing.invoice.domain.InvoiceIssuingReason;
import com.red_escuela.backend.billing.invoice.domain.InvoiceType;
import com.red_escuela.backend.billing.invoice.domain.StudentSnapshot;
import com.red_escuela.backend.billing.invoice.domain.issuing.InvoiceDiscountCandidate;
import com.red_escuela.backend.billing.invoice.domain.issuing.InvoiceIssuingContext;
import com.red_escuela.backend.billing.invoice.domain.issuing.InvoiceIssuingFacts;
import com.red_escuela.backend.billing.invoice.domain.issuing.InvoiceIssuingRequest;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import com.red_escuela.backend.shared.domain.statemachine.StatusChangeActor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

public final class IssuingTestData {

    public static final LocalDate TODAY = LocalDate.of(2027, 3, 10);
    public static final StatusChangeActor ACTOR = StatusChangeActor.ofProcess("TEST");

    private IssuingTestData() {
    }

    public static NonNegativeMoney ars(String amount) {
        return NonNegativeMoney.of(new BigDecimal(amount), Currency.ARS);
    }

    public static InvoiceIssuingRequest request(FeeType feeType, InvoiceIssuingReason reason, LocalDate dueDate, Long templateId, Integer slot) {
        boolean adHoc = reason == InvoiceIssuingReason.AD_HOC_CHARGE;
        return new InvoiceIssuingRequest(1L, 10L, null, 5L, null, null, feeType, YearMonth.from(TODAY), dueDate, TODAY, reason, null, templateId, slot,
                false, adHoc ? ars("100.00") : null, "desc", null, null, adHoc ? "key-1" : null, ACTOR);
    }

    public static InvoiceIssuingRequest monthly() {
        return request(FeeType.MONTHLY_INSTALLMENT, InvoiceIssuingReason.MONTHLY_RUN, TODAY, null, null);
    }

    public static InvoiceIssuingContext context(InvoiceIssuingRequest request, Currency offeringCurrency, Long existingInvoiceId, Integer templateCount) {
        return new InvoiceIssuingContext(request, new InvoiceIssuingFacts(TODAY, offeringCurrency, ars("100.00"), 7L, existingInvoiceId, templateCount,
                new StudentSnapshot("Ana Perez", "123", null)));
    }

    public static InvoiceDiscountCandidate candidate(DiscountType type, long sourceId, InvoiceType invoiceType, String base, String discount) {
        return new InvoiceDiscountCandidate(type, sourceId, sourceId, type.name() + sourceId, invoiceType, ars(base), ars(discount), null, null);
    }
}
