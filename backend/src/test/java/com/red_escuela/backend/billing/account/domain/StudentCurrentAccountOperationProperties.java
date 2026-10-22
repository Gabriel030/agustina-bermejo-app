package com.red_escuela.backend.billing.account.domain;

import com.red_escuela.backend.billing.account.exception.CurrentAccountEntrySourceMismatchException;
import com.red_escuela.backend.billing.account.exception.InsufficientAccountCreditException;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.Money;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.Tag;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.red_escuela.backend.billing.account.domain.AccountTestData.DAY;
import static com.red_escuela.backend.billing.account.domain.AccountTestData.ars;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("D-144")
@Tag("D-159")
@Tag("D-136")
class StudentCurrentAccountOperationProperties {

    @Provide
    Arbitrary<List<BillingOperationCommand>> commands() {
        return BillingOperationCommand.sequences(80);
    }

    @Property(seed = "20261007")
    void everyCommandKeepsTheAccountInvariantsOrIsRejectedWithoutChanges(@ForAll("commands") List<BillingOperationCommand> commands) {
        StudentCurrentAccount account = AccountTestData.arsAccount();
        List<CurrentAccountEntry> entries = new ArrayList<>();
        List<CurrentAccountEntry> paymentCredits = new ArrayList<>();
        Set<Long> reversed = new HashSet<>();
        long nextDocumentId = 1;

        for (BillingOperationCommand command : commands) {
            long sequenceBefore = account.getLastEntrySequence();
            Money balanceBefore = account.calculateSignedBalance();
            BigDecimal unallocatedBefore = account.getUnallocatedCreditAmount().value().amount();
            BigDecimal amount = command.amount();
            BigDecimal unallocated = unallocatedBefore;
            CurrentAccountEntry recorded = null;
            Class<? extends RuntimeException> expectedFailure = null;

            switch (command.kind()) {
                case ISSUE_INVOICE_DEBIT -> {
                    Invoice invoice = AccountTestData.issuedInvoice(nextDocumentId++, amount.toPlainString());
                    recorded = account.recordInvoiceIssuedDebit(invoice, DAY).orElseThrow();
                }
                case ISSUE_DEBIT_NOTE_DEBIT -> {
                    Invoice debitNote = AccountTestData.issuedDebitNote(nextDocumentId++, amount.toPlainString());
                    recorded = account.recordDebitNoteIssuedDebit(debitNote, DAY).orElseThrow();
                }
                case RECEIVE_PAYMENT_CREDIT -> {
                    recorded = AccountTestData.withEntryId(account.recordPaymentReceivedCredit(nextDocumentId++, ars(amount.toPlainString()), DAY, "Pago"));
                    paymentCredits.add(recorded);
                }
                case ISSUE_CREDIT_NOTE_CREDIT -> {
                    recorded = account.recordCreditNoteIssuedCredit(nextDocumentId++, ars(amount.toPlainString()), ars(command.secondAmount().toPlainString()), DAY, "Nota de crédito");
                }
                case REVERSE_PAYMENT_CREDIT -> {
                    if (paymentCredits.isEmpty()) {
                        expectedFailure = CurrentAccountEntrySourceMismatchException.class;
                        CurrentAccountEntry foreign = AccountTestData.withEntryId(AccountTestData.accountWithId(Currency.ARS, 500L)
                                .recordPaymentReceivedCredit(1L, ars("1.00"), DAY, "Pago ajeno"));
                        assertThatThrownBy(() -> account.recordPaymentReversalDebit(foreign, DAY, "Contracargo")).isInstanceOf(expectedFailure);
                    } else {
                        CurrentAccountEntry target = paymentCredits.get(command.pick() % paymentCredits.size());
                        if (reversed.contains(target.getId())) {
                            expectedFailure = CurrentAccountEntrySourceMismatchException.class;
                        } else if (target.getAmount().value().amount().compareTo(unallocated) > 0) {
                            expectedFailure = InsufficientAccountCreditException.class;
                        }
                        if (expectedFailure != null) {
                            assertThatThrownBy(() -> account.recordPaymentReversalDebit(target, DAY, "Contracargo")).isInstanceOf(expectedFailure);
                        } else {
                            recorded = account.recordPaymentReversalDebit(target, DAY, "Contracargo");
                            reversed.add(target.getId());
                        }
                    }
                }
                case PAY_REFUND -> {
                    if (amount.compareTo(unallocated) > 0) {
                        expectedFailure = InsufficientAccountCreditException.class;
                        long refundId = nextDocumentId++;
                        assertThatThrownBy(() -> account.recordRefundPaidDebit(refundId, ars(amount.toPlainString()), DAY, "Devolución")).isInstanceOf(expectedFailure);
                    } else {
                        recorded = account.recordRefundPaidDebit(nextDocumentId++, ars(amount.toPlainString()), DAY, "Devolución");
                    }
                }
            }

            if (recorded == null) {
                assertThat(account.getLastEntrySequence()).isEqualTo(sequenceBefore);
                assertThat(account.calculateSignedBalance()).isEqualTo(balanceBefore);
                assertThat(account.getUnallocatedCreditAmount().value().amount()).isEqualTo(unallocatedBefore);
            } else {
                if (recorded.getEntryType() != CurrentAccountEntryType.PAYMENT_RECEIVED) {
                    AccountTestData.withEntryId(recorded);
                }
                entries.add(recorded);
            }
            assertInvariants(account, entries);
        }
    }

    private static void assertInvariants(StudentCurrentAccount account, List<CurrentAccountEntry> entries) {
        BigDecimal credits = BigDecimal.ZERO;
        BigDecimal debits = BigDecimal.ZERO;
        BigDecimal previousSigned = BigDecimal.ZERO;
        long expectedSequence = 1;
        Set<Long> compensated = new HashSet<>();
        for (CurrentAccountEntry entry : entries) {
            BigDecimal entryAmount = entry.getAmount().value().amount();
            assertThat(entryAmount.signum()).isPositive();
            assertThat(entry.getSequenceNumber()).isEqualTo(expectedSequence++);
            if (entry.getDirection() == CurrentAccountEntryDirection.CREDIT) {
                credits = credits.add(entryAmount);
                previousSigned = previousSigned.add(entryAmount);
            } else {
                debits = debits.add(entryAmount);
                previousSigned = previousSigned.subtract(entryAmount);
            }
            assertThat(entry.getRunningBalance().toSignedAmount().amount()).isEqualByComparingTo(previousSigned);
            if (entry.getCompensatesEntryId() != null) {
                assertThat(compensated.add(entry.getCompensatesEntryId())).isTrue();
            }
        }
        assertThat(account.calculateSignedBalance().amount()).isEqualByComparingTo(credits.subtract(debits));
        assertThat(account.getLastEntrySequence()).isEqualTo(entries.size());
        assertThat(account.getUnallocatedCreditAmount().value().isNegative()).isFalse();
    }
}
