package com.red_escuela.backend.billing.account.domain;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static com.red_escuela.backend.billing.account.domain.AccountTestData.ars;
import static com.red_escuela.backend.billing.account.domain.AccountTestData.arsAccount;
import static com.red_escuela.backend.billing.account.domain.AccountTestData.usd;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-139")
class CreditContributionsTest {

    private static final LocalDate FIRST = LocalDate.of(2027, 3, 5);
    private static final LocalDate SECOND = LocalDate.of(2027, 3, 12);

    private static CreditContributions contributionsOf(String... amounts) {
        List<CreditContribution> list = new ArrayList<>();
        for (int index = 0; index < amounts.length; index++) {
            list.add(new CreditContribution(FIRST.plusDays(index), ars(amounts[index])));
        }
        return new CreditContributions(list);
    }

    @Test
    void onlyPaymentsReceivedAndCreditNotesIssuedContributeCreditAndInOrder() {
        var account = arsAccount();
        List<CurrentAccountEntry> entries = new ArrayList<>();
        var payment = AccountTestData.withEntryId(account.recordPaymentReceivedCredit(1L, ars("100.00"), FIRST, "Pago"));
        entries.add(payment);
        entries.add(account.recordInvoiceIssuedDebit(AccountTestData.issuedInvoice(7L, "40.00"), FIRST).orElseThrow());
        entries.add(account.recordDebitNoteIssuedDebit(AccountTestData.issuedDebitNote(8L, "15.00"), FIRST).orElseThrow());
        entries.add(account.recordCreditNoteIssuedCredit(3L, ars("20.00"), ars("30.00"), SECOND, "NC"));
        entries.add(account.recordPaymentReversalDebit(payment, SECOND, "Reverso"));
        entries.add(account.recordRefundPaidDebit(4L, ars("10.00"), SECOND, "Reintegro"));

        CreditContributions contributions = CreditContributions.fromEntriesOldestFirst(entries);

        assertThat(contributions.asList()).extracting(CreditContribution::effectiveDate, CreditContribution::amount).containsExactly(
                org.assertj.core.api.Assertions.tuple(FIRST, ars("100.00")),
                org.assertj.core.api.Assertions.tuple(SECOND, ars("50.00")));
        assertThat(contributions.asList()).extracting(CreditContribution::paymentId, CreditContribution::creditNoteId)
                .containsExactly(org.assertj.core.api.Assertions.tuple(1L, null), org.assertj.core.api.Assertions.tuple(null, 3L));
    }

    @Test
    void noEntriesGiveNoContributionsAndNothingConsumed() {
        CreditContributions none = CreditContributions.fromEntriesOldestFirst(List.of());

        assertThat(none.asList()).isEmpty();
        assertThat(none.consumedCreditGiven(ars("0.00"))).isEqualTo(ars("0.00"));
    }

    @Test
    void consumedCreditIsTheCreditedTotalMinusWhatIsStillUnallocated() {
        assertThat(contributionsOf("30000.00", "50000.00").consumedCreditGiven(ars("30000.00"))).isEqualTo(ars("50000.00"));
    }

    @Test
    void whenNothingIsUnallocatedEverythingCreditedWasConsumed() {
        assertThat(contributionsOf("30000.00", "50000.00").consumedCreditGiven(ars("0.00"))).isEqualTo(ars("80000.00"));
    }

    @Test
    void whenAllTheCreditIsUnallocatedNothingWasConsumed() {
        assertThat(contributionsOf("30000.00", "50000.00").consumedCreditGiven(ars("80000.00"))).isEqualTo(ars("0.00"));
    }

    @Test
    void oneCentOfConsumedCreditIsKept() {
        assertThat(contributionsOf("100.05").consumedCreditGiven(ars("100.04"))).isEqualTo(ars("0.01"));
        assertThat(contributionsOf("100.05").consumedCreditGiven(ars("0.05"))).isEqualTo(ars("100.00"));
    }

    @Test
    void unallocatedCreditAboveTheCreditedTotalIsClampedToZeroInsteadOfFailing() {
        assertThat(contributionsOf("100.00").consumedCreditGiven(ars("100.01"))).isEqualTo(ars("0.00"));
        assertThat(contributionsOf().consumedCreditGiven(ars("5.00"))).isEqualTo(ars("0.00"));
    }

    @Test
    void theConsumedCreditKeepsTheCurrencyOfTheUnallocatedCredit() {
        assertThat(new CreditContributions(List.of()).consumedCreditGiven(usd("1.00"))).isEqualTo(usd("0.00"));
    }
}
