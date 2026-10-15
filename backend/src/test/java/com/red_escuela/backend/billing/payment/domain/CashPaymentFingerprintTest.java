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

    private static String fingerprint(long studentId, Currency currency, String amount, List<CashPaymentPreview.CoveredInvoice> byCash,
                                      List<CashPaymentPreview.CoveredInvoice> byRemainingCredit, String leftover) {
        return CashPaymentFingerprint.of(studentId, currency, ars(amount), byCash, byRemainingCredit, ars(leftover));
    }

    @Test
    void theSameCoverageProducesTheSameFingerprintRegardlessOfLabels() {
        String first = fingerprint(7L, Currency.ARS, "100000.00", List.of(covered(1, "80000.00")), List.of(), "20000.00");
        String second = fingerprint(7L, Currency.ARS, "100000.00",
                List.of(new CashPaymentPreview.CoveredInvoice(1L, "Otro concepto", LocalDate.of(2030, 1, 1), ars("80000.00"))), List.of(), "20000.00");

        assertThat(first).isEqualTo(second).hasSize(64);
    }

    @Test
    void anyChangeInTheAmountTheCoverageTheLeftoverTheStudentOrTheCurrencyChangesIt() {
        String base = fingerprint(7L, Currency.ARS, "100000.00", List.of(covered(1, "80000.00")), List.of(), "20000.00");

        assertThat(fingerprint(7L, Currency.ARS, "90000.00", List.of(covered(1, "80000.00")), List.of(), "20000.00")).isNotEqualTo(base);
        assertThat(fingerprint(7L, Currency.ARS, "100000.00", List.of(covered(2, "80000.00")), List.of(), "20000.00")).isNotEqualTo(base);
        assertThat(fingerprint(7L, Currency.ARS, "100000.00", List.of(covered(1, "80000.00"), covered(2, "80000.00")), List.of(), "20000.00")).isNotEqualTo(base);
        assertThat(fingerprint(7L, Currency.ARS, "100000.00", List.of(covered(1, "80000.00")), List.of(), "20000.01")).isNotEqualTo(base);
        assertThat(fingerprint(8L, Currency.ARS, "100000.00", List.of(covered(1, "80000.00")), List.of(), "20000.00")).isNotEqualTo(base);
        assertThat(fingerprint(7L, Currency.USD, "100000.00", List.of(covered(1, "80000.00")), List.of(), "20000.00")).isNotEqualTo(base);
        assertThat(fingerprint(7L, Currency.ARS, "100000.00", List.of(), List.of(), "100000.00")).isNotEqualTo(base);
    }

    @Test
    void theInvoiceCoveredByTheRemainingCreditPassIsPartOfTheFingerprint() {
        String paidByCash = fingerprint(7L, Currency.ARS, "100000.00", List.of(covered(1, "80000.00")), List.of(), "20000.00");
        String paidByRemainingCredit = fingerprint(7L, Currency.ARS, "100000.00", List.of(), List.of(covered(1, "80000.00")), "20000.00");
        String withOnlineInvoice = fingerprint(7L, Currency.ARS, "100000.00", List.of(covered(1, "80000.00")), List.of(covered(2, "20000.00")), "0.00");

        assertThat(paidByRemainingCredit).isNotEqualTo(paidByCash);
        assertThat(withOnlineInvoice).isNotEqualTo(paidByCash);
    }
}
