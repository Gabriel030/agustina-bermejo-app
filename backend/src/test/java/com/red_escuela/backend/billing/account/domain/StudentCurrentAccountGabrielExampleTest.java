package com.red_escuela.backend.billing.account.domain;

import com.red_escuela.backend.billing.account.exception.CurrentAccountEntrySourceMismatchException;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.Money;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static com.red_escuela.backend.billing.account.domain.AccountTestData.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("D-144")
@Tag("D-094")
class StudentCurrentAccountGabrielExampleTest {

    private static Money signed(String amount) {
        return Money.of(new BigDecimal(amount), Currency.ARS);
    }

    private final StudentCurrentAccount account = arsAccount();

    private void debitThreeDocuments() {
        CurrentAccountEntry invoice = account.recordInvoiceIssuedDebit(issuedInvoice(11L, "550000.00"), DAY).orElseThrow();
        assertThat(account.calculateSignedBalance()).isEqualTo(signed("-550000.00"));
        assertThat(invoice.getRunningBalance().side()).isEqualTo(CurrentAccountBalanceSide.OWES);
        assertThat(invoice.getRunningBalance().amount()).isEqualTo(ars("550000.00"));

        CurrentAccountEntry surcharge = account.recordDebitNoteIssuedDebit(issuedDebitNote(12L, "50000.00"), DAY).orElseThrow();
        assertThat(surcharge.getRunningBalance().amount()).isEqualTo(ars("600000.00"));

        CurrentAccountEntry enrollment = account.recordInvoiceIssuedDebit(issuedInvoice(13L, "500000.00"), DAY).orElseThrow();
        assertThat(enrollment.getRunningBalance().amount()).isEqualTo(ars("1100000.00"));
        assertThat(account.getBalance().side()).isEqualTo(CurrentAccountBalanceSide.OWES);
        assertThat(account.getLastEntrySequence()).isEqualTo(3);
    }

    @Test
    void aPaymentOfTheExactDebtLeavesTheAccountAtZero() {
        debitThreeDocuments();

        CurrentAccountEntry payment = account.recordPaymentReceivedCredit(21L, ars("1100000.00"), DAY, "Pago");

        assertThat(account.calculateSignedBalance()).isEqualTo(signed("0.00"));
        assertThat(account.getBalance().side()).isEqualTo(CurrentAccountBalanceSide.ZERO);
        assertThat(payment.getRunningBalance()).isEqualTo(CurrentAccountBalance.zero(Currency.ARS));
        assertThat(account.getUnallocatedCreditAmount()).isEqualTo(ars("1100000.00"));
    }

    @Test
    void aBiggerPaymentLeavesTheDifferenceInFavorAndReversingItRestoresTheDebtWithoutTouchingTheOriginalCredit() {
        debitThreeDocuments();
        CurrentAccountEntry payment = withEntryId(account.recordPaymentReceivedCredit(21L, ars("1200000.00"), DAY, "Pago"));

        assertThat(account.calculateSignedBalance()).isEqualTo(signed("100000.00"));
        assertThat(account.getBalance().side()).isEqualTo(CurrentAccountBalanceSide.IN_FAVOR);
        assertThat(account.getUnallocatedCreditAmount()).isEqualTo(ars("1200000.00"));

        CurrentAccountEntry reversal = account.recordPaymentReversalDebit(payment, DAY, "Contracargo");

        assertThat(reversal.getDirection()).isEqualTo(CurrentAccountEntryDirection.DEBIT);
        assertThat(reversal.getEntryType()).isEqualTo(CurrentAccountEntryType.PAYMENT_REVERSED);
        assertThat(reversal.getAmount()).isEqualTo(ars("1200000.00"));
        assertThat(reversal.getCompensatesEntryId()).isEqualTo(payment.getId());
        assertThat(reversal.getPaymentId()).isEqualTo(21L);
        assertThat(payment.getAmount()).isEqualTo(ars("1200000.00"));
        assertThat(payment.getDirection()).isEqualTo(CurrentAccountEntryDirection.CREDIT);
        assertThat(account.calculateSignedBalance()).isEqualTo(signed("-1100000.00"));
        assertThat(account.getUnallocatedCreditAmount()).isEqualTo(ars("0.00"));
    }

    @Test
    void reversingTheSameCreditTwiceIsRejected() {
        CurrentAccountEntry payment = withEntryId(account.recordPaymentReceivedCredit(21L, ars("100.00"), DAY, "Pago"));
        account.recordPaymentReversalDebit(payment, DAY, "Contracargo");
        long sequenceBefore = account.getLastEntrySequence();

        assertThatThrownBy(() -> account.recordPaymentReversalDebit(payment, DAY, "Otra vez")).isInstanceOf(CurrentAccountEntrySourceMismatchException.class);
        assertThat(account.getLastEntrySequence()).isEqualTo(sequenceBefore);
    }
}
