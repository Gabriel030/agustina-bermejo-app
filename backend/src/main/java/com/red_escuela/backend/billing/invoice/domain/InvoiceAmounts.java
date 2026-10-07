package com.red_escuela.backend.billing.invoice.domain;

import com.red_escuela.backend.billing.invoice.exception.InvalidInvoiceAmountsException;
import com.red_escuela.backend.billing.invoice.exception.PartialPaymentNotAllowedException;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.CurrencyMismatchException;
import com.red_escuela.backend.shared.domain.Money;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Embedded;

import java.util.List;
import java.util.Objects;

@Embeddable
public record InvoiceAmounts(
        @Embedded
        @AttributeOverrides({
                @AttributeOverride(name = "value.amount", column = @Column(name = "lines_total", nullable = false)),
                @AttributeOverride(name = "value.currency", column = @Column(name = "lines_total_currency", nullable = false))})
        NonNegativeMoney linesTotal,
        @Embedded
        @AttributeOverrides({
                @AttributeOverride(name = "value.amount", column = @Column(name = "discounts_total", nullable = false)),
                @AttributeOverride(name = "value.currency", column = @Column(name = "discounts_total_currency", nullable = false))})
        NonNegativeMoney discountsTotal,
        @Embedded
        @AttributeOverrides({
                @AttributeOverride(name = "value.amount", column = @Column(name = "total", nullable = false)),
                @AttributeOverride(name = "value.currency", column = @Column(name = "total_currency", nullable = false))})
        NonNegativeMoney total,
        @Embedded
        @AttributeOverrides({
                @AttributeOverride(name = "value.amount", column = @Column(name = "paid_amount", nullable = false)),
                @AttributeOverride(name = "value.currency", column = @Column(name = "paid_amount_currency", nullable = false))})
        NonNegativeMoney paidAmount,
        @Embedded
        @AttributeOverrides({
                @AttributeOverride(name = "value.amount", column = @Column(name = "credited_amount", nullable = false)),
                @AttributeOverride(name = "value.currency", column = @Column(name = "credited_amount_currency", nullable = false))})
        NonNegativeMoney creditedAmount,
        @Embedded
        @AttributeOverrides({
                @AttributeOverride(name = "value.amount", column = @Column(name = "outstanding_amount", nullable = false)),
                @AttributeOverride(name = "value.currency", column = @Column(name = "outstanding_amount_currency", nullable = false))})
        NonNegativeMoney outstandingAmount) {

    public InvoiceAmounts {
        Objects.requireNonNull(linesTotal, "linesTotal");
        Objects.requireNonNull(discountsTotal, "discountsTotal");
        Objects.requireNonNull(total, "total");
        Objects.requireNonNull(paidAmount, "paidAmount");
        Objects.requireNonNull(creditedAmount, "creditedAmount");
        Objects.requireNonNull(outstandingAmount, "outstandingAmount");
        Currency currency = linesTotal.value().currency();
        for (NonNegativeMoney amount : List.of(discountsTotal, total, paidAmount, creditedAmount, outstandingAmount)) {
            if (amount.value().currency() != currency) {
                throw new CurrencyMismatchException(currency, amount.value().currency());
            }
        }
        if (!linesTotal.minus(discountsTotal).equals(total.value())) {
            throw new InvalidInvoiceAmountsException("total must be lines total minus discounts total");
        }
        if (!total.value().minus(paidAmount.value()).minus(creditedAmount.value()).equals(outstandingAmount.value())) {
            throw new InvalidInvoiceAmountsException("outstanding must be total minus paid minus credited");
        }
        if (!paidAmount.value().isZero() && !outstandingAmount.value().isZero()) {
            throw new InvalidInvoiceAmountsException("a paid invoice cannot have an outstanding amount");
        }
    }

    public static InvoiceAmounts zero(Currency currency) {
        NonNegativeMoney zero = NonNegativeMoney.zero(currency);
        return new InvoiceAmounts(zero, zero, zero, zero, zero, zero);
    }

    public static InvoiceAmounts calculateDraftAmounts(Currency currency, List<InvoiceLine> lines) {
        NonNegativeMoney linesTotal = NonNegativeMoney.zero(currency);
        NonNegativeMoney discountsTotal = NonNegativeMoney.zero(currency);
        for (InvoiceLine line : lines) {
            linesTotal = linesTotal.plus(line.getAmount());
            discountsTotal = discountsTotal.plus(line.getDiscountAmount());
        }
        NonNegativeMoney total = NonNegativeMoney.of(linesTotal.minus(discountsTotal));
        NonNegativeMoney zero = NonNegativeMoney.zero(currency);
        return new InvoiceAmounts(linesTotal, discountsTotal, total, zero, zero, total);
    }

    public InvoiceAmounts withFullPaymentAllocated(NonNegativeMoney allocatedAmount) {
        Objects.requireNonNull(allocatedAmount, "allocatedAmount");
        if (allocatedAmount.value().currency() != total.value().currency()) {
            throw new CurrencyMismatchException(total.value().currency(), allocatedAmount.value().currency());
        }
        if (allocatedAmount.value().isZero() || !allocatedAmount.equals(outstandingAmount)) {
            throw new PartialPaymentNotAllowedException();
        }
        return new InvoiceAmounts(linesTotal, discountsTotal, total, paidAmount.plus(allocatedAmount), creditedAmount,
                NonNegativeMoney.zero(total.value().currency()));
    }

    public InvoiceAmounts withPaymentAllocationReversed() {
        NonNegativeMoney restored = NonNegativeMoney.of(total.minus(creditedAmount));
        return new InvoiceAmounts(linesTotal, discountsTotal, total, NonNegativeMoney.zero(total.value().currency()),
                creditedAmount, restored);
    }

    public InvoiceAmounts withCreditNoteOffset(NonNegativeMoney offsetAmount) {
        Objects.requireNonNull(offsetAmount, "offsetAmount");
        Money remaining = outstandingAmount.minus(offsetAmount);
        return new InvoiceAmounts(linesTotal, discountsTotal, total, paidAmount, creditedAmount.plus(offsetAmount),
                NonNegativeMoney.of(remaining));
    }

    public boolean hasOutstandingAmount() {
        return !outstandingAmount.value().isZero();
    }

    public boolean hasPaidAmount() {
        return !paidAmount.value().isZero();
    }
}
