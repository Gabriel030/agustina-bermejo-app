package com.red_escuela.backend.billing.issuing;

import com.red_escuela.backend.billing.entity.PriceRevision;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.MonetaryAdjustment;
import com.red_escuela.backend.shared.domain.Money;
import com.red_escuela.backend.shared.domain.Percentage;

import java.time.LocalDate;

public record IssuanceContext(ChargeRequest request, LocalDate today, Currency offeringCurrency, Money amount, PriceRevision priceRevision,
                              Long existingInvoiceId, Percentage entryFeeDiscount, MonetaryAdjustment upfrontDiscount) {

    public boolean alreadyIssued() {
        return existingInvoiceId != null;
    }
}
