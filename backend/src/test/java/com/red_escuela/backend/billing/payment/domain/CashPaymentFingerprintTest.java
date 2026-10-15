package com.red_escuela.backend.billing.payment.domain;

import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-319")
@Tag("D-207")
class CashPaymentFingerprintTest {

    private static NonNegativeMoney ars(String amount) {
        return NonNegativeMoney.of(new BigDecimal(amount), Currency.ARS);
    }

    private static CashPaymentPreview.CoveredInvoice covered(long invoiceId, String amount) {
        return new CashPaymentPreview.CoveredInvoice(invoiceId, "Cuota", LocalDate.of(2027, 3, 10), ars(amount));
    }

    @Test
    void theSameCoverageProducesTheSameFingerprintRegardlessOfLabels() {
        String first = CashPaymentFingerprint.of(7L, Currency.ARS, List.of(covered(1, "80000.00")), ars("20000.00"));
        String second = CashPaymentFingerprint.of(7L, Currency.ARS,
                List.of(new CashPaymentPreview.CoveredInvoice(1L, "Otro concepto", LocalDate.of(2030, 1, 1), ars("80000.00"))), ars("20000.00"));

        assertThat(first).isEqualTo(second).hasSize(64);
    }

    @Test
    void anyChangeInTheCoveredInvoicesTheLeftoverTheStudentOrTheCurrencyChangesIt() {
        String base = CashPaymentFingerprint.of(7L, Currency.ARS, List.of(covered(1, "80000.00")), ars("20000.00"));

        assertThat(CashPaymentFingerprint.of(7L, Currency.ARS, List.of(covered(2, "80000.00")), ars("20000.00"))).isNotEqualTo(base);
        assertThat(CashPaymentFingerprint.of(7L, Currency.ARS, List.of(covered(1, "80000.00"), covered(2, "80000.00")), ars("20000.00"))).isNotEqualTo(base);
        assertThat(CashPaymentFingerprint.of(7L, Currency.ARS, List.of(covered(1, "80000.00")), ars("20000.01"))).isNotEqualTo(base);
        assertThat(CashPaymentFingerprint.of(8L, Currency.ARS, List.of(covered(1, "80000.00")), ars("20000.00"))).isNotEqualTo(base);
        assertThat(CashPaymentFingerprint.of(7L, Currency.USD, List.of(covered(1, "80000.00")), ars("20000.00"))).isNotEqualTo(base);
        assertThat(CashPaymentFingerprint.of(7L, Currency.ARS, List.of(), ars("100000.00"))).isNotEqualTo(base);
    }
}
