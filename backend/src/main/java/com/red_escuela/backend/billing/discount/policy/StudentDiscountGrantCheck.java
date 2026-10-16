package com.red_escuela.backend.billing.discount.policy;

import com.red_escuela.backend.billing.invoice.domain.DiscountType;
import com.red_escuela.backend.billing.invoice.domain.InvoiceType;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.MonetaryAdjustment;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Objects;

public record StudentDiscountGrantCheck(DiscountType discountType, InvoiceType invoiceType, MonetaryAdjustment calculation, NonNegativeMoney capPerInvoice,
                                        Currency enrollmentCurrency, YearMonth from, YearMonth to, YearMonth nextInstallmentPeriod,
                                        boolean annualUpfrontCoversRange, String reason, DefinitionFault definitionFault, LocalDate approvalDeadline,
                                        LocalDate today) {

    public enum DefinitionFault {
        NONE,
        MISSING,
        NOT_A_SCHOLARSHIP,
        CLOSED,
        UNEXPECTED_FOR_INDIVIDUAL
    }

    public StudentDiscountGrantCheck(DiscountType discountType, InvoiceType invoiceType, MonetaryAdjustment calculation, NonNegativeMoney capPerInvoice,
                                     Currency enrollmentCurrency, YearMonth from, YearMonth to, YearMonth nextInstallmentPeriod, boolean annualUpfrontCoversRange, String reason) {
        this(discountType, invoiceType, calculation, capPerInvoice, enrollmentCurrency, from, to, nextInstallmentPeriod, annualUpfrontCoversRange, reason, DefinitionFault.NONE, null, null);
    }

    public StudentDiscountGrantCheck(DiscountType discountType, InvoiceType invoiceType, MonetaryAdjustment calculation, NonNegativeMoney capPerInvoice,
                                     Currency enrollmentCurrency, YearMonth from, YearMonth to, YearMonth nextInstallmentPeriod, boolean annualUpfrontCoversRange, String reason,
                                     DefinitionFault definitionFault) {
        this(discountType, invoiceType, calculation, capPerInvoice, enrollmentCurrency, from, to, nextInstallmentPeriod, annualUpfrontCoversRange, reason, definitionFault, null, null);
    }


    public StudentDiscountGrantCheck {
        Objects.requireNonNull(discountType, "discountType");
        Objects.requireNonNull(invoiceType, "invoiceType");
        Objects.requireNonNull(enrollmentCurrency, "enrollmentCurrency");
        Objects.requireNonNull(nextInstallmentPeriod, "nextInstallmentPeriod");
        Objects.requireNonNull(definitionFault, "definitionFault");
    }
}
