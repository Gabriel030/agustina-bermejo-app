package com.red_escuela.backend.billing.collections.domain;

import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.Money;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.red_escuela.backend.shared.domain.TestMoney.ars;
import static com.red_escuela.backend.shared.domain.TestMoney.usd;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-339")
class OverdueContactSelectionTest {

    private static final OverdueContactCriteria CRITERIA = new OverdueContactCriteria(14, 2);

    private static OverdueAccountBalance balance(long studentId, int installments, int oldestDays, Money amount) {
        return new OverdueAccountBalance(studentId, installments, oldestDays, amount);
    }

    @Test
    void eachStudentIsJudgedOnItsOwnBalancesNotOnTheWholeSet() {
        List<OverdueContact> members = OverdueContactSelection.membersOf(CRITERIA, List.of(
                balance(1L, 1, 10, ars(1000)), balance(2L, 1, 10, ars(1000)), balance(3L, 1, 10, ars(1000))));

        assertThat(members).isEmpty();
    }

    @Test
    void onlyTheStudentWithAnOldDebtOrTwoInstallmentsEntersTheList() {
        List<OverdueContact> members = OverdueContactSelection.membersOf(CRITERIA, List.of(
                balance(1L, 1, 14, ars(1000)), balance(2L, 1, 13, ars(2000)), balance(3L, 2, 2, ars(3000))));

        assertThat(members).extracting(OverdueContact::studentId).containsExactly(1L, 3L);
    }

    @Test
    void installmentsOfDifferentCurrenciesCountTogetherButTheAmountsStayApart() {
        List<OverdueContact> members = OverdueContactSelection.membersOf(CRITERIA, List.of(
                balance(7L, 1, 3, ars(100000)), balance(7L, 1, 5, usd(100))));

        assertThat(members).singleElement().satisfies(contact -> {
            assertThat(contact.overdueInstallments()).isEqualTo(2);
            assertThat(contact.oldestDaysOverdue()).isEqualTo(5);
            assertThat(contact.overdueAmounts()).containsExactly(ars(100000), usd(100));
            assertThat(contact.owesIn(Currency.ARS)).isTrue();
            assertThat(contact.owesIn(Currency.USD)).isTrue();
        });
    }

    @Test
    void balancesOfTheSameCurrencyAreAddedAndNeverMixedWithAnotherCurrency() {
        List<OverdueContact> members = OverdueContactSelection.membersOf(CRITERIA, List.of(
                balance(7L, 1, 20, ars(100000)), balance(7L, 1, 3, ars(50000)), balance(7L, 1, 1, usd(10))));

        assertThat(members).singleElement().satisfies(contact -> {
            assertThat(contact.overdueAmounts()).containsExactly(ars(150000), usd(10));
            assertThat(contact.overdueInstallments()).isEqualTo(3);
        });
    }

    @Test
    void membersAreListedMostOverdueFirstAndTiesByStudent() {
        List<OverdueContact> members = OverdueContactSelection.membersOf(CRITERIA, List.of(
                balance(5L, 1, 20, ars(1)), balance(2L, 1, 40, ars(1)), balance(1L, 1, 20, ars(1))));

        assertThat(members).extracting(OverdueContact::studentId).containsExactly(2L, 1L, 5L);
    }

    @Test
    void aStudentOwingInOneCurrencyOnlyDoesNotOweInTheOther() {
        OverdueContact contact = OverdueContactSelection.membersOf(CRITERIA, List.of(balance(9L, 1, 30, ars(1000)))).get(0);

        assertThat(contact.owesIn(Currency.ARS)).isTrue();
        assertThat(contact.owesIn(Currency.USD)).isFalse();
    }

    @Test
    void studentsNewlyInAreTheOnesOfTodayThatWereNotInYesterdaysList() {
        List<OverdueContact> yesterday = OverdueContactSelection.membersOf(CRITERIA, List.of(balance(1L, 1, 15, ars(1)), balance(2L, 1, 20, ars(1))));
        List<OverdueContact> today = OverdueContactSelection.membersOf(CRITERIA, List.of(balance(1L, 1, 16, ars(1)), balance(3L, 1, 14, ars(1))));

        assertThat(OverdueContactSelection.studentsNewlyIn(today, yesterday)).containsExactly(3L);
    }

    @Test
    void anEmptyListHasNoNewcomers() {
        assertThat(OverdueContactSelection.studentsNewlyIn(List.of(), List.of())).isEmpty();
        assertThat(OverdueContactSelection.membersOf(CRITERIA, List.of())).isEmpty();
    }
}
