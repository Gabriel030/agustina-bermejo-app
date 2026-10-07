package com.red_escuela.backend.billing.invoice.domain;

import com.red_escuela.backend.billing.invoice.exception.InvalidInvoiceAmountsException;
import com.red_escuela.backend.billing.invoice.exception.PartialPaymentNotAllowedException;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.CurrencyMismatchException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static com.red_escuela.backend.billing.invoice.domain.InvoiceTestData.ars;
import static com.red_escuela.backend.billing.invoice.domain.InvoiceTestData.usd;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("D-099")
@Tag("D-136")
@Tag("D-171")
class InvoiceAmountsTest {

    private static InvoiceAmounts openAmounts() {
        return new InvoiceAmounts(ars("80000.00"), ars("40000.00"), ars("40000.00"), ars("0.00"), ars("0.00"), ars("40000.00"));
    }

    @Test
    void totalIsLinesMinusDiscountsAndOutstandingIsTotalMinusPaidMinusCredited() {
        InvoiceAmounts amounts = new InvoiceAmounts(ars("80000.00"), ars("40000.00"), ars("40000.00"), ars("0.00"), ars("10000.00"), ars("30000.00"));

        assertThat(amounts.total()).isEqualTo(ars("40000.00"));
        assertThat(amounts.outstandingAmount()).isEqualTo(ars("30000.00"));
        assertThat(amounts.hasOutstandingAmount()).isTrue();
        assertThat(amounts.hasPaidAmount()).isFalse();
    }

    @Test
    void aTotalThatIsNotLinesMinusDiscountsIsRejected() {
        assertThatThrownBy(() -> new InvoiceAmounts(ars("80000.00"), ars("40000.00"), ars("40000.01"), ars("0.00"), ars("0.00"), ars("40000.01")))
                .isInstanceOf(InvalidInvoiceAmountsException.class);
    }

    @Test
    void anOutstandingThatIsNotTotalMinusPaidMinusCreditedIsRejected() {
        assertThatThrownBy(() -> new InvoiceAmounts(ars("80000.00"), ars("0.00"), ars("80000.00"), ars("0.00"), ars("0.00"), ars("79999.99")))
                .isInstanceOf(InvalidInvoiceAmountsException.class);
    }

    @Test
    void discountsGreaterThanLinesAreRejectedAsAnInvalidTotal() {
        assertThatThrownBy(() -> new InvoiceAmounts(ars("10.00"), ars("20.00"), ars("0.00"), ars("0.00"), ars("0.00"), ars("0.00")))
                .isInstanceOf(InvalidInvoiceAmountsException.class);
    }

    @Test
    void paidWithOutstandingIsRejected() {
        assertThatThrownBy(() -> new InvoiceAmounts(ars("80000.00"), ars("0.00"), ars("80000.00"), ars("30000.00"), ars("0.00"), ars("50000.00")))
                .isInstanceOf(InvalidInvoiceAmountsException.class);
    }

    @Test
    void mixedCurrenciesAreRejectedInEveryComponent() {
        assertThatThrownBy(() -> new InvoiceAmounts(ars("1.00"), usd("0.00"), ars("1.00"), ars("0.00"), ars("0.00"), ars("1.00")))
                .isInstanceOf(CurrencyMismatchException.class);
        assertThatThrownBy(() -> new InvoiceAmounts(ars("1.00"), ars("0.00"), usd("1.00"), ars("0.00"), ars("0.00"), ars("1.00")))
                .isInstanceOf(CurrencyMismatchException.class);
        assertThatThrownBy(() -> new InvoiceAmounts(ars("1.00"), ars("0.00"), ars("1.00"), usd("0.00"), ars("0.00"), ars("1.00")))
                .isInstanceOf(CurrencyMismatchException.class);
        assertThatThrownBy(() -> new InvoiceAmounts(ars("1.00"), ars("0.00"), ars("1.00"), ars("0.00"), usd("0.00"), ars("1.00")))
                .isInstanceOf(CurrencyMismatchException.class);
        assertThatThrownBy(() -> new InvoiceAmounts(ars("1.00"), ars("0.00"), ars("1.00"), ars("0.00"), ars("0.00"), usd("1.00")))
                .isInstanceOf(CurrencyMismatchException.class);
    }

    @Test
    void nullComponentsAreRejected() {
        assertThatThrownBy(() -> new InvoiceAmounts(null, ars("0.00"), ars("0.00"), ars("0.00"), ars("0.00"), ars("0.00"))).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new InvoiceAmounts(ars("0.00"), null, ars("0.00"), ars("0.00"), ars("0.00"), ars("0.00"))).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new InvoiceAmounts(ars("0.00"), ars("0.00"), null, ars("0.00"), ars("0.00"), ars("0.00"))).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new InvoiceAmounts(ars("0.00"), ars("0.00"), ars("0.00"), null, ars("0.00"), ars("0.00"))).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new InvoiceAmounts(ars("0.00"), ars("0.00"), ars("0.00"), ars("0.00"), null, ars("0.00"))).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new InvoiceAmounts(ars("0.00"), ars("0.00"), ars("0.00"), ars("0.00"), ars("0.00"), null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void zeroAmountsAreAllZeroInTheGivenCurrency() {
        InvoiceAmounts zero = InvoiceAmounts.zero(Currency.USD);

        assertThat(zero.total()).isEqualTo(usd("0.00"));
        assertThat(zero.hasOutstandingAmount()).isFalse();
    }

    @Test
    void fullPaymentOfTheOutstandingAmountPaysTheInvoice() {
        InvoiceAmounts paid = openAmounts().withFullPaymentAllocated(ars("40000.00"));

        assertThat(paid.paidAmount()).isEqualTo(ars("40000.00"));
        assertThat(paid.outstandingAmount()).isEqualTo(ars("0.00"));
        assertThat(paid.hasPaidAmount()).isTrue();
        assertThat(paid.creditedAmount()).isEqualTo(ars("0.00"));
    }

    @Test
    void aPaymentDifferentFromTheOutstandingAmountIsRejected() {
        assertThatThrownBy(() -> openAmounts().withFullPaymentAllocated(ars("30000.00"))).isInstanceOf(PartialPaymentNotAllowedException.class);
        assertThatThrownBy(() -> openAmounts().withFullPaymentAllocated(ars("40000.01"))).isInstanceOf(PartialPaymentNotAllowedException.class);
        assertThatThrownBy(() -> openAmounts().withFullPaymentAllocated(ars("0.00"))).isInstanceOf(PartialPaymentNotAllowedException.class);
    }

    @Test
    void aPaymentInAnotherCurrencyIsRejected() {
        assertThatThrownBy(() -> openAmounts().withFullPaymentAllocated(usd("40000.00"))).isInstanceOf(CurrencyMismatchException.class);
    }

    @Test
    void aZeroPaymentOnAnAlreadySettledInvoiceIsStillRejected() {
        InvoiceAmounts settled = InvoiceAmounts.zero(Currency.ARS);

        assertThatThrownBy(() -> settled.withFullPaymentAllocated(ars("0.00"))).isInstanceOf(PartialPaymentNotAllowedException.class);
    }

    @Test
    void reversingTheAllocationRestoresTheOutstandingAmountMinusCredits() {
        InvoiceAmounts withCredit = openAmounts().withCreditNoteOffset(ars("10000.00")).withFullPaymentAllocated(ars("30000.00"));

        InvoiceAmounts reversed = withCredit.withPaymentAllocationReversed();

        assertThat(reversed.paidAmount()).isEqualTo(ars("0.00"));
        assertThat(reversed.outstandingAmount()).isEqualTo(ars("30000.00"));
        assertThat(reversed.creditedAmount()).isEqualTo(ars("10000.00"));
    }

    @Test
    void aCreditNoteOffsetLowersTheOutstandingAmountAndRaisesTheCredited() {
        InvoiceAmounts credited = openAmounts().withCreditNoteOffset(ars("15000.50"));

        assertThat(credited.creditedAmount()).isEqualTo(ars("15000.50"));
        assertThat(credited.outstandingAmount()).isEqualTo(ars("24999.50"));
    }

    @Test
    void calculatingDraftAmountsAddsLinesAndDiscounts() {
        Invoice invoice = InvoiceTestData.monthlyDraft("80000.00");
        invoice.addDiscountToDraftLine(1, InvoiceTestData.discount("80000.00", "40000.00"));

        InvoiceAmounts amounts = InvoiceAmounts.calculateDraftAmounts(Currency.ARS, invoice.getLines());

        assertThat(amounts.linesTotal()).isEqualTo(ars("80000.00"));
        assertThat(amounts.discountsTotal()).isEqualTo(ars("40000.00"));
        assertThat(amounts.total()).isEqualTo(ars("40000.00"));
        assertThat(amounts.outstandingAmount()).isEqualTo(ars("40000.00"));
        assertThat(amounts.paidAmount()).isEqualTo(ars("0.00"));
        assertThat(amounts.creditedAmount()).isEqualTo(ars("0.00"));
    }
}
