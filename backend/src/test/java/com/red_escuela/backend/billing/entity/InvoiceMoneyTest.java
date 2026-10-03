package com.red_escuela.backend.billing.entity;

import com.red_escuela.backend.shared.domain.CurrencyMismatchException;
import com.red_escuela.backend.shared.domain.MonetaryAdjustment;
import com.red_escuela.backend.shared.domain.Percentage;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static com.red_escuela.backend.shared.domain.TestMoney.ars;
import static com.red_escuela.backend.shared.domain.TestMoney.usd;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InvoiceMoneyTest {

    private Invoice pendingInvoice(com.red_escuela.backend.shared.domain.Money base) {
        Invoice invoice = new Invoice();
        invoice.setBaseAmount(base);
        invoice.setStatus(InvoiceStatus.PENDING);
        return invoice;
    }

    @Test
    void aTenPercentSurchargeOnFortyEightThousandIsFourThousandEightHundredAndTheBalanceFiftyTwoThousandEightHundred() {
        Invoice invoice = pendingInvoice(ars(48000));

        invoice.markOverdue(new MonetaryAdjustment.Proportional(Percentage.ofPoints(BigDecimal.TEN)), "Mora automática");

        assertThat(invoice.getAdjustments()).singleElement()
                .satisfies(adjustment -> assertThat(adjustment.signedAmount()).isEqualTo(ars("4800.00")));
        assertThat(invoice.getRemainingBalance()).isEqualTo(ars("52800.00"));
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.OVERDUE);
    }

    @Test
    void aPaymentInAnotherCurrencyIsNotAppliedToTheInvoice() {
        Invoice invoice = pendingInvoice(usd(100));

        assertThatThrownBy(() -> invoice.registerPayment(ars(100), new Payment()))
                .isInstanceOf(CurrencyMismatchException.class);
        assertThat(invoice.getAllocations()).isEmpty();
    }

    @Test
    void aNominalAdjustmentInAnotherCurrencyIsRejected() {
        Invoice invoice = pendingInvoice(usd(100));

        assertThatThrownBy(() -> invoice.applyAdjustment(InvoiceAdjustmentType.DISCOUNT,
                new MonetaryAdjustment.Nominal(ars(10)), "Descuento", null))
                .isInstanceOf(CurrencyMismatchException.class);
    }
}
