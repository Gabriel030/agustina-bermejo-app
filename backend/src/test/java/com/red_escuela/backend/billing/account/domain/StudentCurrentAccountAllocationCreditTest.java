package com.red_escuela.backend.billing.account.domain;

import com.red_escuela.backend.billing.account.exception.InsufficientAccountCreditException;
import com.red_escuela.backend.billing.invoice.exception.StudentCurrentAccountCurrencyMismatchException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static com.red_escuela.backend.billing.account.domain.AccountTestData.ars;
import static com.red_escuela.backend.billing.account.domain.AccountTestData.usd;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("D-144")
class StudentCurrentAccountAllocationCreditTest {

    private StudentCurrentAccount accountWithCredit(String amount) {
        StudentCurrentAccount account = AccountTestData.arsAccount();
        account.recordPaymentReceivedCredit(77L, ars(amount), LocalDate.of(2027, 3, 1), "Pago");
        return account;
    }

    @Test
    void consumingCreditForAnAllocationMovesOnlyTheUnallocatedCreditAndNotTheBalance() {
        StudentCurrentAccount account = accountWithCredit("100.00");

        account.consumeUnallocatedCreditForAllocation(ars("60.00"));

        assertThat(account.getUnallocatedCreditAmount()).isEqualTo(ars("40.00"));
        assertThat(account.calculateSignedBalance().amount()).isEqualByComparingTo("100.00");
        assertThat(account.getLastEntrySequence()).isEqualTo(1);
    }

    @Test
    void consumingMoreCreditThanAvailableIsRejectedAndKeepsTheCredit() {
        StudentCurrentAccount account = accountWithCredit("100.00");

        assertThatThrownBy(() -> account.consumeUnallocatedCreditForAllocation(ars("100.01"))).isInstanceOf(InsufficientAccountCreditException.class);
        assertThat(account.getUnallocatedCreditAmount()).isEqualTo(ars("100.00"));
    }

    @Test
    void consumingExactlyTheAvailableCreditLeavesZero() {
        StudentCurrentAccount account = accountWithCredit("100.00");

        account.consumeUnallocatedCreditForAllocation(ars("100.00"));

        assertThat(account.getUnallocatedCreditAmount()).isEqualTo(ars("0.00"));
    }

    @Test
    void restoringCreditFromAReversedAllocationAddsItBackWithoutAnEntry() {
        StudentCurrentAccount account = accountWithCredit("100.00");
        account.consumeUnallocatedCreditForAllocation(ars("100.00"));

        account.restoreUnallocatedCreditFromReversedAllocation(ars("80.00"));

        assertThat(account.getUnallocatedCreditAmount()).isEqualTo(ars("80.00"));
        assertThat(account.getLastEntrySequence()).isEqualTo(1);
    }

    @Test
    void creditOperationsInAnotherCurrencyAreRejected() {
        StudentCurrentAccount account = accountWithCredit("100.00");

        assertThatThrownBy(() -> account.consumeUnallocatedCreditForAllocation(usd("1.00"))).isInstanceOf(StudentCurrentAccountCurrencyMismatchException.class);
        assertThatThrownBy(() -> account.restoreUnallocatedCreditFromReversedAllocation(usd("1.00"))).isInstanceOf(StudentCurrentAccountCurrencyMismatchException.class);
    }

    @Test
    void requireUnallocatedCreditCoversAllowsTheExactAmountAndRejectsOneCentMore() {
        StudentCurrentAccount account = accountWithCredit("100.00");

        account.requireUnallocatedCreditCovers(ars("100.00"));

        assertThatThrownBy(() -> account.requireUnallocatedCreditCovers(ars("100.01"))).isInstanceOf(InsufficientAccountCreditException.class);
    }
}
