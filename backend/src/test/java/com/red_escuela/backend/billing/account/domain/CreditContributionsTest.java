package com.red_escuela.backend.billing.account.domain;

import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.red_escuela.backend.billing.account.domain.AccountTestData.ars;
import static com.red_escuela.backend.billing.account.domain.AccountTestData.arsAccount;
import static com.red_escuela.backend.billing.account.domain.AccountTestData.withEntryId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

@Tag("D-139")
@Tag("D-376")
class CreditContributionsTest {

    private static final LocalDate FIRST = LocalDate.of(2027, 3, 5);
    private static final LocalDate SECOND = LocalDate.of(2027, 3, 12);

    private static CreditContributions available(List<CurrentAccountEntry> entries, Map<Long, NonNegativeMoney> sourced, String unallocated) {
        return CreditContributions.availableFrom(entries, sourced, ars(unallocated));
    }

    @Test
    void onlyThePaymentsAndTheFreePartOfCreditNotesContributeAndInOrder() {
        var account = arsAccount();
        List<CurrentAccountEntry> entries = new ArrayList<>();
        entries.add(withEntryId(account.recordPaymentReceivedCredit(1L, ars("100.00"), FIRST, "Pago")));
        entries.add(account.recordInvoiceIssuedDebit(AccountTestData.issuedInvoice(7L, "40.00"), FIRST).orElseThrow());
        entries.add(withEntryId(account.recordCreditNoteIssuedCredit(3L, ars("20.00"), ars("30.00"), SECOND, "NC")));

        CreditContributions contributions = available(entries, Map.of(), "130.00");

        assertThat(contributions.asList()).extracting(CreditContribution::effectiveDate, CreditContribution::amount, CreditContribution::paymentId, CreditContribution::creditNoteId)
                .containsExactly(tuple(FIRST, ars("100.00"), 1L, null), tuple(SECOND, ars("30.00"), null, 3L));
    }

    @Test
    void aCreditNoteThatOnlyOffsetsDebtContributesNoCredit() {
        var account = arsAccount();
        List<CurrentAccountEntry> entries = new ArrayList<>();
        entries.add(withEntryId(account.recordPaymentReceivedCredit(1L, ars("100.00"), FIRST, "Pago")));
        entries.add(withEntryId(account.recordCreditNoteIssuedCredit(3L, ars("150.00"), ars("0.00"), SECOND, "NC")));

        assertThat(available(entries, Map.of(), "100.00").asList()).extracting(CreditContribution::paymentId).containsExactly(1L);
    }

    @Test
    void aReversedPaymentIsNeverAvailable() {
        var account = arsAccount();
        List<CurrentAccountEntry> entries = new ArrayList<>();
        CurrentAccountEntry payment = withEntryId(account.recordPaymentReceivedCredit(1L, ars("100.00"), FIRST, "Pago"));
        entries.add(payment);
        entries.add(withEntryId(account.recordPaymentReceivedCredit(2L, ars("60.00"), SECOND, "Pago")));
        entries.add(withEntryId(account.recordPaymentReversalDebit(payment, SECOND, "Rebote")));

        assertThat(available(entries, Map.of(), "60.00").asList()).extracting(CreditContribution::paymentId).containsExactly(2L);
    }

    @Test
    void aReversedPaymentInTheMiddleIsSkippedWhileTheOlderAndNewerOnesStay() {
        var account = arsAccount();
        List<CurrentAccountEntry> entries = new ArrayList<>();
        entries.add(withEntryId(account.recordPaymentReceivedCredit(1L, ars("50.00"), FIRST, "Pago")));
        CurrentAccountEntry middle = withEntryId(account.recordPaymentReceivedCredit(2L, ars("100.00"), FIRST, "Pago"));
        entries.add(middle);
        entries.add(withEntryId(account.recordPaymentReceivedCredit(3L, ars("60.00"), SECOND, "Pago")));
        entries.add(withEntryId(account.recordPaymentReversalDebit(middle, SECOND, "Rebote")));

        assertThat(available(entries, Map.of(), "110.00").asList()).extracting(CreditContribution::paymentId, CreditContribution::amount)
                .containsExactly(tuple(1L, ars("50.00")), tuple(3L, ars("60.00")));
    }

    @Test
    void unallocatedCreditAboveWhatCanBeTracedFailsEarlyWithAClearMessage() {
        var account = arsAccount();
        CurrentAccountEntry payment = withEntryId(account.recordPaymentReceivedCredit(1L, ars("100.00"), FIRST, "Pago"));

        assertThatThrownBy(() -> available(List.of(payment), Map.of(), "100.01")).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("exceeds the credit that can be traced");
        assertThatThrownBy(() -> available(List.of(), Map.of(), "5.00")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void whatEachEntryAlreadyFundsIsDiscountedFromItsRoom() {
        var account = arsAccount();
        List<CurrentAccountEntry> entries = new ArrayList<>();
        CurrentAccountEntry first = withEntryId(account.recordPaymentReceivedCredit(1L, ars("100.00"), FIRST, "Pago"));
        entries.add(first);
        entries.add(withEntryId(account.recordPaymentReceivedCredit(2L, ars("100.00"), SECOND, "Pago")));

        CreditContributions contributions = available(entries, Map.of(first.getId(), ars("100.00")), "100.00");

        assertThat(contributions.asList()).extracting(CreditContribution::paymentId, CreditContribution::amount).containsExactly(tuple(2L, ars("100.00")));
    }

    @Test
    void creditThatLeftTheAccountWithoutBeingAllocatedIsTakenFromTheOldestEntryFirst() {
        var account = arsAccount();
        List<CurrentAccountEntry> entries = new ArrayList<>();
        entries.add(withEntryId(account.recordPaymentReceivedCredit(1L, ars("100.00"), FIRST, "Pago")));
        entries.add(withEntryId(account.recordPaymentReceivedCredit(2L, ars("100.00"), SECOND, "Pago")));

        CreditContributions contributions = available(entries, Map.of(), "120.00");

        assertThat(contributions.asList()).extracting(CreditContribution::paymentId, CreditContribution::amount)
                .containsExactly(tuple(1L, ars("20.00")), tuple(2L, ars("100.00")));
    }

    @Test
    void anEntryTakenBeyondItsAllocatableAmountIsRejected() {
        var account = arsAccount();
        CurrentAccountEntry payment = withEntryId(account.recordPaymentReceivedCredit(1L, ars("100.00"), FIRST, "Pago"));

        assertThatThrownBy(() -> available(List.of(payment), Map.of(payment.getId(), ars("100.01")), "0.00")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void noEntriesGiveNoContributions() {
        assertThat(available(List.of(), Map.of(), "0.00").asList()).isEmpty();
    }
}
