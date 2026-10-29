package com.red_escuela.backend.billing.run.operation;

import com.red_escuela.backend.billing.account.service.AbstractCurrentAccountIT;
import com.red_escuela.backend.billing.invariant.BillingInvariantChecker;
import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.domain.InvoiceDocumentType;
import com.red_escuela.backend.billing.invoice.domain.InvoiceDraftHeader;
import com.red_escuela.backend.billing.invoice.domain.InvoiceFactory;
import com.red_escuela.backend.billing.invoice.domain.InvoiceIssuingReason;
import com.red_escuela.backend.billing.invoice.domain.InvoiceLineDraft;
import com.red_escuela.backend.billing.invoice.domain.InvoiceType;
import com.red_escuela.backend.billing.run.domain.AccountConsistencyCheck;
import com.red_escuela.backend.billing.run.domain.AccountConsistencyFinding;
import com.red_escuela.backend.billing.support.BillingTestFixtures;
import com.red_escuela.backend.shared.domain.Currency;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.stream.Stream;

import static com.red_escuela.backend.billing.account.domain.AccountTestData.ars;
import static com.red_escuela.backend.billing.invoice.domain.InvoiceTestData.ACTOR;
import static com.red_escuela.backend.billing.invoice.domain.InvoiceTestData.SNAPSHOT;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-356")
@Tag("D-459")
class AccountConsistencyScannerIT extends AbstractCurrentAccountIT {
    private static final LocalDate DUE = LocalDate.of(2027, 3, 10);
    private static final LocalDate NIGHT = LocalDate.of(2027, 6, 1);

    @Autowired
    private AccountConsistencyScanner scanner;

    private Long newAccount() {
        return newAccountId(newStudentId(), Currency.ARS);
    }

    private List<AccountConsistencyFinding> findingsOf(LocalDate businessDate, Long... accountIds) {
        List<Long> wanted = List.of(accountIds);
        return scanner.scanAllAccounts(businessDate).findings().stream().filter(finding -> wanted.contains(finding.accountId())).toList();
    }

    private Long issueWithoutLedgerDebit(Long accountId, String chargeKey) {
        return inTransaction(() -> {
            InvoiceDraftHeader header = new InvoiceDraftHeader(accountId, Currency.ARS, InvoiceDocumentType.INVOICE, InvoiceType.ADMINISTRATIVE_FEE,
                    InvoiceIssuingReason.AD_HOC_CHARGE, null);
            InvoiceLineDraft line = new InvoiceLineDraft(FeeType.ADMINISTRATIVE_FEE, "Concepto", YearMonth.of(2027, 3), null, null, null, null, null, chargeKey, ars("40000.00"));
            Invoice draft = InvoiceFactory.createAdministrativeFeeInvoiceDraft(header, line, ACTOR);
            draft.issueInvoice(numberGenerator.nextInvoiceNumber(InvoiceDocumentType.INVOICE), DUE.minusDays(9), DUE, SNAPSHOT, ACTOR);
            return invoiceRepository.save(draft).getId();
        });
    }

    @Test
    void anAccountWhoseBalanceDiffersFromItsEntriesByThirtyFiveThousandIsFlaggedWithTheDifferenceInPesos() {
        BillingInvariantChecker invariants = BillingInvariantChecker.startingNow(jdbc);
        Long broken = newAccount();
        issueAdministrativeFeeWithLedgerDebit(broken, "50000.00", DUE);
        Long healthy = newAccount();
        issueAdministrativeFeeWithLedgerDebit(healthy, "50000.00", DUE);
        jdbc.update("update student_current_account set balance_amount = balance_amount + 35000 where id = ?", broken);

        List<AccountConsistencyFinding> findings = findingsOf(NIGHT, broken, healthy);

        assertThat(findings).singleElement().satisfies(finding -> {
            assertThat(finding.accountId()).isEqualTo(broken);
            assertThat(finding.findingCode()).isEqualTo("INCONSISTENT_ACCOUNT");
            assertThat(finding.detail()).isEqualTo("account " + broken + " difference 35000.00");
        });
        assertThat(invariants.accountBalanceEqualsCreditsMinusDebits())
                .contains(AccountConsistencyCheck.BALANCE_DIFFERS_FROM_ENTRIES.invariant() + ": " + findings.get(0).detail());
    }

    @Test
    void anIssuedInvoiceWithoutItsDebitAndAnApprovedPaymentWithoutItsCreditAreFlaggedWithTheirOwnCodes() {
        BillingInvariantChecker invariants = BillingInvariantChecker.startingNow(jdbc);
        Long withoutDebit = newAccount();
        issueAdministrativeFeeWithLedgerDebit(withoutDebit, "50000.00", DUE);
        Long orphanInvoice = issueWithoutLedgerDebit(withoutDebit, "NODEBIT:" + System.nanoTime());
        Long withoutCredit = newAccount();
        long orphanPayment = BillingTestFixtures.seedApprovedPaymentRow(withoutCredit);
        Long withCredit = newAccount();
        long creditedPayment = BillingTestFixtures.seedApprovedPaymentRow(withCredit, ars("5000.00"));
        inTransaction(() -> service.recordPaymentReceivedCredit(service.lockStudentCurrentAccount(withCredit), creditedPayment, ars("5000.00"), DUE, "Pago"));

        List<AccountConsistencyFinding> findings = findingsOf(NIGHT, withoutDebit, withoutCredit, withCredit);

        assertThat(findings).extracting(AccountConsistencyFinding::accountId, AccountConsistencyFinding::findingCode, AccountConsistencyFinding::detail)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(withoutDebit, "ISSUED_INVOICE_WITHOUT_DEBIT", "invoice " + orphanInvoice),
                        org.assertj.core.groups.Tuple.tuple(withoutCredit, "APPROVED_PAYMENT_WITHOUT_CREDIT", "payment " + orphanPayment));
        assertThat(invariants.accountBalanceIsTheUnallocatedCreditMinusTheOutstandingInvoices())
                .contains(AccountConsistencyCheck.ISSUED_INVOICE_WITHOUT_ITS_DEBIT.invariant() + ": invoice " + orphanInvoice);
    }

    @Test
    void aLiveChargeKeyHeldByTwoInvoicesFlagsBothAccountsAndAReleasedOrDiscardedHolderIsIgnored() {
        BillingInvariantChecker invariants = BillingInvariantChecker.startingNow(jdbc);
        Long first = newAccount();
        Long second = newAccount();
        Long released = newAccount();
        Long discarded = newAccount();
        Long firstInvoice = issueAdministrativeFeeWithLedgerDebit(first, "10000.00", DUE);
        Long secondInvoice = issueAdministrativeFeeWithLedgerDebit(second, "10000.00", DUE);
        Long releasedInvoice = issueAdministrativeFeeWithLedgerDebit(released, "10000.00", DUE);
        Long discardedInvoice = issueAdministrativeFeeWithLedgerDebit(discarded, "10000.00", DUE);
        String sharedKey = "DUP:" + System.nanoTime();
        jdbc.execute("drop index uk_invoice_line_charge_key");
        jdbc.execute("alter table invoice_line disable trigger trg_invoice_line_immutable_when_issued");
        try {
            jdbc.update("update invoice_line set charge_key = ? where invoice_id in (?, ?, ?, ?)", sharedKey, firstInvoice, secondInvoice, releasedInvoice, discardedInvoice);
            jdbc.update("update invoice_line set charge_key_released_at = now() where invoice_id = ?", releasedInvoice);
            jdbc.update("update invoice set document_status = 'DISCARDED' where id = ?", discardedInvoice);

            List<AccountConsistencyFinding> findings = findingsOf(NIGHT, first, second, released, discarded);

            assertThat(findings).extracting(AccountConsistencyFinding::accountId, AccountConsistencyFinding::findingCode, AccountConsistencyFinding::detail)
                    .containsExactlyInAnyOrder(
                            org.assertj.core.groups.Tuple.tuple(first, "DUPLICATE_LIVE_CHARGE_KEY", "charge key " + sharedKey),
                            org.assertj.core.groups.Tuple.tuple(second, "DUPLICATE_LIVE_CHARGE_KEY", "charge key " + sharedKey));
            assertThat(invariants.aChargeKeyIsHeldByAtMostOneLiveLine())
                    .contains(AccountConsistencyCheck.CHARGE_KEY_HELD_BY_MORE_THAN_ONE_LIVE_LINE.invariant() + ": charge key " + sharedKey);
        } finally {
            jdbc.update("update invoice_line set charge_key = 'FIXED:' || invoice_id where invoice_id in (?, ?, ?, ?)", firstInvoice, secondInvoice, releasedInvoice, discardedInvoice);
            jdbc.execute("alter table invoice_line enable trigger trg_invoice_line_immutable_when_issued");
            jdbc.execute("create unique index uk_invoice_line_charge_key on invoice_line (charge_key) where charge_key_released_at is null");
        }
    }

    @Test
    void aStoredPaymentStatusThatDisagreesWithTheAmountsOrAnOverdueMarkBeforeTheDueDateIsFlaggedAndALegitOverdueIsNot() {
        BillingInvariantChecker invariants = BillingInvariantChecker.startingNow(jdbc);
        Long paidWithDebt = newAccount();
        Long paidInvoice = issueAdministrativeFeeWithLedgerDebit(paidWithDebt, "20000.00", DUE);
        Long overdueTooSoon = newAccount();
        Long earlyInvoice = issueAdministrativeFeeWithLedgerDebit(overdueTooSoon, "20000.00", DUE);
        Long overdueOnTheDueDate = newAccount();
        Long boundaryInvoice = issueAdministrativeFeeWithLedgerDebit(overdueOnTheDueDate, "20000.00", DUE);
        Long overdueAfter = newAccount();
        Long lateInvoice = issueAdministrativeFeeWithLedgerDebit(overdueAfter, "20000.00", DUE);
        jdbc.execute("alter table invoice drop constraint ck_invoice_paid_status_amounts");
        try {
            jdbc.update("update invoice set payment_status = 'PAID' where id = ?", paidInvoice);
            jdbc.update("update invoice set payment_status = 'OVERDUE' where id in (?, ?, ?)", earlyInvoice, boundaryInvoice, lateInvoice);
            assertStatusMismatchesAreFlagged(invariants, paidWithDebt, overdueTooSoon, overdueOnTheDueDate, overdueAfter, paidInvoice);
        } finally {
            jdbc.update("update invoice set payment_status = 'PENDING' where id in (?, ?, ?, ?)", paidInvoice, earlyInvoice, boundaryInvoice, lateInvoice);
            jdbc.execute("alter table invoice add constraint ck_invoice_paid_status_amounts check (payment_status <> 'PAID' or outstanding_amount = 0)");
        }
    }

    private void assertStatusMismatchesAreFlagged(BillingInvariantChecker invariants, Long paidWithDebt, Long overdueTooSoon, Long overdueOnTheDueDate, Long overdueAfter, Long paidInvoice) {

        List<AccountConsistencyFinding> atTheDueDate = findingsOf(DUE, paidWithDebt, overdueTooSoon, overdueOnTheDueDate, overdueAfter);
        List<AccountConsistencyFinding> afterTheDueDate = findingsOf(DUE.plusDays(1), paidWithDebt, overdueTooSoon, overdueOnTheDueDate, overdueAfter);

        assertThat(atTheDueDate).extracting(AccountConsistencyFinding::accountId, AccountConsistencyFinding::findingCode)
                .containsExactlyInAnyOrder(org.assertj.core.groups.Tuple.tuple(paidWithDebt, "PAYMENT_STATUS_MISMATCH"), org.assertj.core.groups.Tuple.tuple(overdueTooSoon, "PAYMENT_STATUS_MISMATCH"),
                        org.assertj.core.groups.Tuple.tuple(overdueOnTheDueDate, "PAYMENT_STATUS_MISMATCH"), org.assertj.core.groups.Tuple.tuple(overdueAfter, "PAYMENT_STATUS_MISMATCH"));
        assertThat(afterTheDueDate).extracting(AccountConsistencyFinding::accountId).containsExactly(paidWithDebt);
        assertThat(afterTheDueDate.get(0).detail()).isEqualTo("invoice " + paidInvoice + " PAID");
        assertThat(invariants.paymentStatusIsConsistentWithAmounts()).contains(AccountConsistencyCheck.PAYMENT_STATUS_DIFFERS_FROM_AMOUNTS.invariant() + ": invoice " + paidInvoice + " PAID");
    }

    @Test
    void everyCheckHasItsOwnInvariantAndTheOnlyOneThatTakesTheBusinessDateIsTheOverdueCheck() {
        assertThat(Stream.of(AccountConsistencyCheck.values()).filter(AccountConsistencyCheck::takesBusinessDate)).containsExactly(AccountConsistencyCheck.OVERDUE_STATUS_BEFORE_THE_DUE_DATE);
        assertThat(AccountConsistencyCheck.inPriorityOrder()).containsExactly(AccountConsistencyCheck.values());
        assertThat(Stream.of(AccountConsistencyCheck.values()).map(AccountConsistencyCheck::findingCode)).containsExactly("INCONSISTENT_ACCOUNT", "ISSUED_INVOICE_WITHOUT_DEBIT",
                "APPROVED_PAYMENT_WITHOUT_CREDIT", "DUPLICATE_LIVE_CHARGE_KEY", "PAYMENT_STATUS_MISMATCH", "PAYMENT_STATUS_MISMATCH");
        assertThat(Stream.of(AccountConsistencyCheck.values()).map(AccountConsistencyCheck::invariant)).doesNotContainNull().allMatch(invariant -> !invariant.isBlank());
    }
}
