package com.red_escuela.backend.billing.invoice.domain;

import com.red_escuela.backend.billing.entity.PriceRevision;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.MonetaryAdjustment;
import com.red_escuela.backend.shared.domain.Money;
import com.red_escuela.backend.shared.domain.Percentage;

import java.time.LocalDate;

public record InvoiceIssuingContext(InvoiceIssuingRequest request, LocalDate today, Currency offeringCurrency, Money amount, PriceRevision priceRevision,
                              Long existingInvoiceId, Percentage entryFeeDiscount, MonetaryAdjustment upfrontDiscount,
                              Integer templateEnrollmentFeeCount) {

    public InvoiceIssuingContext(InvoiceIssuingRequest request, LocalDate today, Currency offeringCurrency, Money amount, PriceRevision priceRevision,
                           Long existingInvoiceId, Percentage entryFeeDiscount, MonetaryAdjustment upfrontDiscount) {
        this(request, today, offeringCurrency, amount, priceRevision, existingInvoiceId, entryFeeDiscount, upfrontDiscount, null);
    }

    public boolean alreadyIssued() {
        return existingInvoiceId != null;
    }
}
