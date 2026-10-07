package com.red_escuela.backend.billing.account.domain;

import com.red_escuela.backend.billing.account.exception.CurrentAccountEntrySourceMismatchException;
import com.red_escuela.backend.billing.account.exception.InsufficientAccountCreditException;
import com.red_escuela.backend.billing.invoice.exception.StudentCurrentAccountCurrencyMismatchException;
import com.red_escuela.backend.shared.domain.Currency;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Optional;

import static com.red_escuela.backend.billing.account.domain.AccountTestData.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("D-144")
@Tag("D-097")
@Tag("D-150")
@Tag("D-159")
@Tag("D-139")
@Tag("D-193")
class StudentCurrentAccountRecordingTest {

    private final StudentCurrentAccount account = arsAccount();

    private void assertUntouched(long sequence) {
        assertThat(account.getLastEntrySequence()).isEqualTo(sequence);
    }

    @Test
    void anInvoiceDebitCopiesTheTotalTheNumberAndTheSource() {
        CurrentAccountEntry entry = account.recordInvoiceIssuedDebit(issuedInvoice(5L, "80000.00"), DAY).orElseThrow();

        assertThat(entry.getEntryType()).isEqualTo(CurrentAccountEntryType.INVOICE_ISSUED);
        assertThat(entry.getDirection()).isEqualTo(CurrentAccountEntryDirection.DEBIT);
        assertThat(entry.getAmount()).isEqualTo(ars("80000.00"));
        assertThat(entry.getInvoiceId()).isEqualTo(5L);
        assertThat(entry.getConcept()).isEqualTo("Factura F-00000005");
        assertThat(entry.getSequenceNumber()).isEqualTo(1);
        assertThat(entry.getStudentCurrentAccountId()).isEqualTo(ACCOUNT_ID);
        assertThat(entry.getCurrency()).isEqualTo(Currency.ARS);
        assertThat(entry.getEffectiveDate()).isEqualTo(DAY);
        assertThat(entry.getRecordedAt()).isNotNull();
        assertThat(entry.getPaymentId()).isNull();
        assertThat(entry.getCreditNoteId()).isNull();
        assertThat(entry.getRefundId()).isNull();
        assertThat(entry.getCompensatesEntryId()).isNull();
    }

    @Test
    void aDebitNoteDebitUsesItsOwnTypeAndConcept() {
        CurrentAccountEntry entry = account.recordDebitNoteIssuedDebit(issuedDebitNote(6L, "5000.00"), DAY).orElseThrow();

        assertThat(entry.getEntryType()).isEqualTo(CurrentAccountEntryType.DEBIT_NOTE_ISSUED);
        assertThat(entry.getConcept()).isEqualTo("Nota de débito ND-00000006");
    }

    @Test
    void anUnissuedInvoiceCannotBeDebited() {
        assertThatThrownBy(() -> account.recordInvoiceIssuedDebit(draftInvoice(7L, "10.00"), DAY)).isInstanceOf(CurrentAccountEntrySourceMismatchException.class);
        assertUntouched(0);
    }

    @Test
    void anInvoiceOfAnotherAccountCannotBeDebited() {
        assertThatThrownBy(() -> account.recordInvoiceIssuedDebit(issuedInvoice(8L, 99L, Currency.ARS, "10.00", "PE:8:X"), DAY))
                .isInstanceOf(CurrentAccountEntrySourceMismatchException.class);
        assertUntouched(0);
    }

    @Test
    void aDebitNoteGoesThroughItsOwnMethodAndAnInvoiceThroughItsOwn() {
        assertThatThrownBy(() -> account.recordInvoiceIssuedDebit(issuedDebitNote(9L, "10.00"), DAY)).isInstanceOf(CurrentAccountEntrySourceMismatchException.class);
        assertThatThrownBy(() -> account.recordDebitNoteIssuedDebit(issuedInvoice(10L, "10.00"), DAY)).isInstanceOf(CurrentAccountEntrySourceMismatchException.class);
        assertUntouched(0);
    }

    @Test
    void anInvoiceWithoutIdCannotBeDebited() {
        com.red_escuela.backend.billing.invoice.domain.Invoice invoice = issuedInvoice(11L, "10.00");
        org.springframework.test.util.ReflectionTestUtils.setField(invoice, "id", null);

        assertThatThrownBy(() -> account.recordInvoiceIssuedDebit(invoice, DAY)).isInstanceOf(CurrentAccountEntrySourceMismatchException.class);
    }

    @Test
    void anInvoiceInAnotherCurrencyIsRejected() {
        assertThatThrownBy(() -> account.recordInvoiceIssuedDebit(issuedInvoice(12L, ACCOUNT_ID, Currency.USD, "10.00", "PE:12:X"), DAY))
                .isInstanceOf(StudentCurrentAccountCurrencyMismatchException.class);
        assertUntouched(0);
    }

    @Test
    void aZeroTotalInvoiceDoesNotMoveTheAccount() {
        Optional<CurrentAccountEntry> entry = account.recordInvoiceIssuedDebit(issuedZeroTotalInvoice(13L), DAY);

        assertThat(entry).isEmpty();
        assertUntouched(0);
        assertThat(account.getBalance()).isEqualTo(CurrentAccountBalance.zero(Currency.ARS));
    }

    @Test
    void aPaymentCreditRaisesTheUnallocatedCreditAndKeepsTheInvoicePending() {
        account.recordInvoiceIssuedDebit(issuedInvoice(14L, "80000.00"), DAY);

        CurrentAccountEntry payment = account.recordPaymentReceivedCredit(30L, ars("30000.00"), DAY, "Pago parcial");

        assertThat(payment.getEntryType()).isEqualTo(CurrentAccountEntryType.PAYMENT_RECEIVED);
        assertThat(payment.getPaymentId()).isEqualTo(30L);
        assertThat(payment.getConcept()).isEqualTo("Pago parcial");
        assertThat(account.getUnallocatedCreditAmount()).isEqualTo(ars("30000.00"));
        assertThat(account.getBalance().amount()).isEqualTo(ars("50000.00"));
        assertThat(account.getBalance().side()).isEqualTo(CurrentAccountBalanceSide.OWES);
    }

    @Test
    void aPaymentAmountMustBePositiveAndInTheAccountCurrency() {
        assertThatThrownBy(() -> account.recordPaymentReceivedCredit(31L, ars("0.00"), DAY, "Pago")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> account.recordPaymentReceivedCredit(31L, usd("1.00"), DAY, "Pago")).isInstanceOf(StudentCurrentAccountCurrencyMismatchException.class);
        assertUntouched(0);
    }

    @Test
    void aCreditNoteRaisesTheUnallocatedCreditOnlyByTheFreePart() {
        CurrentAccountEntry entry = account.recordCreditNoteIssuedCredit(40L, ars("0.00"), ars("40000.00"), DAY, "Nota de crédito");

        assertThat(entry.getEntryType()).isEqualTo(CurrentAccountEntryType.CREDIT_NOTE_ISSUED);
        assertThat(entry.getCreditNoteId()).isEqualTo(40L);
        assertThat(account.getUnallocatedCreditAmount()).isEqualTo(ars("40000.00"));
        assertThat(account.getBalance().amount()).isEqualTo(ars("40000.00"));

        account.recordCreditNoteIssuedCredit(41L, ars("80000.00"), ars("0.00"), DAY, "Cancelación");

        assertThat(account.getUnallocatedCreditAmount()).isEqualTo(ars("40000.00"));
        assertThat(account.getBalance().amount()).isEqualTo(ars("120000.00"));
    }

    @Test
    void aCreditNoteMustCarryAPositiveTotalInTheAccountCurrency() {
        assertThatThrownBy(() -> account.recordCreditNoteIssuedCredit(42L, ars("0.00"), ars("0.00"), DAY, "Nada")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> account.recordCreditNoteIssuedCredit(42L, usd("1.00"), ars("1.00"), DAY, "Mixta")).isInstanceOf(StudentCurrentAccountCurrencyMismatchException.class);
        assertThatThrownBy(() -> account.recordCreditNoteIssuedCredit(42L, ars("1.00"), usd("1.00"), DAY, "Mixta")).isInstanceOf(StudentCurrentAccountCurrencyMismatchException.class);
        assertUntouched(0);
    }

    @Test
    void aReversalNeedsAPaymentCreditOfThisAccountThatWasNotReversedBefore() {
        CurrentAccountEntry payment = withEntryId(account.recordPaymentReceivedCredit(50L, ars("100.00"), DAY, "Pago"));
        CurrentAccountEntry creditNote = withEntryId(account.recordCreditNoteIssuedCredit(51L, ars("0.00"), ars("100.00"), DAY, "NC"));
        CurrentAccountEntry otherAccountPayment = withEntryId(accountWithId(Currency.ARS, 77L).recordPaymentReceivedCredit(52L, ars("100.00"), DAY, "Pago"));
        CurrentAccountEntry unsaved = account.recordPaymentReceivedCredit(53L, ars("100.00"), DAY, "Pago sin id");
        org.springframework.test.util.ReflectionTestUtils.setField(unsaved, "id", null);
        long sequence = account.getLastEntrySequence();

        assertThatThrownBy(() -> account.recordPaymentReversalDebit(creditNote, DAY, "x")).isInstanceOf(CurrentAccountEntrySourceMismatchException.class);
        assertThatThrownBy(() -> account.recordPaymentReversalDebit(otherAccountPayment, DAY, "x")).isInstanceOf(CurrentAccountEntrySourceMismatchException.class);
        assertThatThrownBy(() -> account.recordPaymentReversalDebit(unsaved, DAY, "x")).isInstanceOf(CurrentAccountEntrySourceMismatchException.class);
        assertUntouched(sequence);
        assertThat(account.recordPaymentReversalDebit(payment, DAY, "Contracargo").getCompensatesEntryId()).isEqualTo(payment.getId());
    }

    @Test
    void aReversalBiggerThanTheUnallocatedCreditIsRejectedWithoutChanges() {
        CurrentAccountEntry payment = withEntryId(account.recordPaymentReceivedCredit(60L, ars("100.00"), DAY, "Pago"));
        account.recordRefundPaidDebit(61L, ars("30.00"), DAY, "Devolución");
        long sequence = account.getLastEntrySequence();

        assertThatThrownBy(() -> account.recordPaymentReversalDebit(payment, DAY, "Contracargo")).isInstanceOf(InsufficientAccountCreditException.class);

        assertUntouched(sequence);
        assertThat(account.getUnallocatedCreditAmount()).isEqualTo(ars("70.00"));
        assertThat(account.getBalance().amount()).isEqualTo(ars("70.00"));
    }

    @Test
    void aRefundComesOutOfTheUnallocatedCredit() {
        account.recordPaymentReceivedCredit(70L, ars("100.00"), DAY, "Pago");

        CurrentAccountEntry refund = account.recordRefundPaidDebit(71L, ars("40.00"), DAY, "Devolución");

        assertThat(refund.getEntryType()).isEqualTo(CurrentAccountEntryType.REFUND_PAID);
        assertThat(refund.getRefundId()).isEqualTo(71L);
        assertThat(account.getUnallocatedCreditAmount()).isEqualTo(ars("60.00"));
        assertThat(account.getBalance().amount()).isEqualTo(ars("60.00"));
        assertThat(account.getBalance().side()).isEqualTo(CurrentAccountBalanceSide.IN_FAVOR);
    }

    @Test
    void aRefundBiggerThanTheUnallocatedCreditIsRejectedWithoutChanges() {
        account.recordPaymentReceivedCredit(72L, ars("100.00"), DAY, "Pago");

        assertThatThrownBy(() -> account.recordRefundPaidDebit(73L, ars("100.01"), DAY, "Devolución")).isInstanceOf(InsufficientAccountCreditException.class);
        assertThatThrownBy(() -> account.recordRefundPaidDebit(73L, ars("0.00"), DAY, "Devolución")).isInstanceOf(IllegalArgumentException.class);

        assertUntouched(1);
        assertThat(account.getUnallocatedCreditAmount()).isEqualTo(ars("100.00"));
    }

    @Test
    void aRefundOfTheWholeUnallocatedCreditIsAccepted() {
        account.recordPaymentReceivedCredit(74L, ars("100.00"), DAY, "Pago");

        account.recordRefundPaidDebit(75L, ars("100.00"), DAY, "Devolución");

        assertThat(account.getUnallocatedCreditAmount()).isEqualTo(ars("0.00"));
        assertThat(account.getBalance()).isEqualTo(CurrentAccountBalance.zero(Currency.ARS));
    }

    @Test
    void theConceptAndTheEffectiveDateAreRequired() {
        assertThatThrownBy(() -> account.recordPaymentReceivedCredit(80L, ars("1.00"), DAY, " ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> account.recordPaymentReceivedCredit(80L, ars("1.00"), DAY, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> account.recordPaymentReceivedCredit(80L, ars("1.00"), DAY, "x".repeat(201))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> account.recordPaymentReceivedCredit(80L, ars("1.00"), (LocalDate) null, "Pago")).isInstanceOf(NullPointerException.class);
        assertThat(account.recordPaymentReceivedCredit(80L, ars("1.00"), DAY, "x".repeat(200)).getConcept()).hasSize(200);
        assertUntouched(1);
    }

    @Test
    void everyEntryAdvancesTheSequenceByOneAndCarriesTheRunningBalance() {
        CurrentAccountEntry first = account.recordInvoiceIssuedDebit(issuedInvoice(90L, "10.00"), DAY).orElseThrow();
        CurrentAccountEntry second = account.recordPaymentReceivedCredit(91L, ars("25.00"), DAY, "Pago");

        assertThat(first.getSequenceNumber()).isEqualTo(1);
        assertThat(second.getSequenceNumber()).isEqualTo(2);
        assertThat(first.getRunningBalance().toSignedAmount().amount()).isEqualByComparingTo("-10.00");
        assertThat(second.getRunningBalance().toSignedAmount().amount()).isEqualByComparingTo("15.00");
        assertThat(account.getLastEntrySequence()).isEqualTo(2);
    }

    @Test
    void aCreditRememberedAsReversedCannotBeReversedAgain() {
        CurrentAccountEntry payment = withEntryId(account.recordPaymentReceivedCredit(95L, ars("100.00"), DAY, "Pago"));
        account.rememberReversedCreditEntry(payment.getId());

        assertThatThrownBy(() -> account.recordPaymentReversalDebit(payment, DAY, "Contracargo")).isInstanceOf(CurrentAccountEntrySourceMismatchException.class);
        assertThatThrownBy(() -> account.rememberReversedCreditEntry(null)).isInstanceOf(NullPointerException.class);
        assertUntouched(1);
    }
}
