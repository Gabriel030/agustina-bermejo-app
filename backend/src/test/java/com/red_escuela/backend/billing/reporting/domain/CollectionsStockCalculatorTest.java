package com.red_escuela.backend.billing.reporting.domain;

import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.Money;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CollectionsStockCalculatorTest {

    private static final LocalDate END_OF_MARCH = LocalDate.of(2026, 3, 31);

    private static Money ars(String amount) {
        return Money.of(new BigDecimal(amount), Currency.ARS);
    }

    private static Money usd(String amount) {
        return Money.of(new BigDecimal(amount), Currency.USD);
    }

    private static OverdueInvoiceFact fact(long invoiceId, long studentId, int daysOverdue, Money outstanding, Set<Long> programs, Set<Long> courses) {
        return new OverdueInvoiceFact(invoiceId, studentId, END_OF_MARCH.minusDays(daysOverdue), outstanding, programs, courses);
    }

    private static CollectionsStockRow rowOf(List<CollectionsStockRow> rows, Currency currency, CollectionsStockScope scope) {
        return rows.stream().filter(row -> row.currency() == currency && row.scope().equals(scope)).findFirst().orElseThrow();
    }

    @Test
    @Tag("D-146")
    void eachInvoiceFallsInTheBucketOfItsOwnAgeAndEveryStudentIsCountedOnce() {
        List<CollectionsStockRow> rows = CollectionsStockCalculator.calculateStock(END_OF_MARCH, List.of(
                fact(1, 1, 10, ars("100000.00"), Set.of(), Set.of()), fact(2, 2, 45, ars("100000.00"), Set.of(), Set.of()),
                fact(3, 3, 70, ars("100000.00"), Set.of(), Set.of()), fact(4, 4, 120, ars("100000.00"), Set.of(), Set.of())));

        CollectionsStockRow total = rowOf(rows, Currency.ARS, CollectionsStockScope.TOTAL);

        for (OverdueAgeBucket bucket : OverdueAgeBucket.values()) {
            assertThat(total.overdueIn(bucket)).isEqualTo(ars("100000.00"));
        }
        assertThat(total.studentsWithOverdue()).isEqualTo(4);
        assertThat(total.totalOverdue()).isEqualTo(ars("400000.00"));
    }

    @Test
    @Tag("D-146")
    void theSameStudentWithManyOverdueInvoicesCountsOnceAndKeepsEachInvoiceInItsOwnBucket() {
        List<CollectionsStockRow> rows = CollectionsStockCalculator.calculateStock(END_OF_MARCH, List.of(
                fact(1, 7, 10, ars("1000.00"), Set.of(), Set.of()), fact(2, 7, 40, ars("2000.00"), Set.of(), Set.of()), fact(3, 7, 100, ars("4000.00"), Set.of(), Set.of())));

        CollectionsStockRow total = rowOf(rows, Currency.ARS, CollectionsStockScope.TOTAL);

        assertThat(total.studentsWithOverdue()).isEqualTo(1);
        assertThat(total.overdueIn(OverdueAgeBucket.FROM_1_TO_30)).isEqualTo(ars("1000.00"));
        assertThat(total.overdueIn(OverdueAgeBucket.FROM_31_TO_60)).isEqualTo(ars("2000.00"));
        assertThat(total.overdueIn(OverdueAgeBucket.OVER_90)).isEqualTo(ars("4000.00"));
    }

    @Test
    @Tag("D-097")
    void eachCurrencyGetsItsOwnRowsAndMoneyOfDifferentCurrenciesIsNeverAdded() {
        List<CollectionsStockRow> rows = CollectionsStockCalculator.calculateStock(END_OF_MARCH, List.of(
                fact(1, 1, 10, ars("5000.00"), Set.of(9L), Set.of()), fact(2, 2, 10, usd("50.00"), Set.of(8L), Set.of())));

        assertThat(rowOf(rows, Currency.ARS, CollectionsStockScope.TOTAL).totalOverdue()).isEqualTo(ars("5000.00"));
        assertThat(rowOf(rows, Currency.USD, CollectionsStockScope.TOTAL).totalOverdue()).isEqualTo(usd("50.00"));
        assertThat(rows.stream().filter(row -> row.scope().equals(CollectionsStockScope.ofProgram(9L)))).singleElement().extracting(CollectionsStockRow::currency).isEqualTo(Currency.ARS);
        assertThat(rows.stream().filter(row -> row.scope().equals(CollectionsStockScope.ofProgram(8L)))).singleElement().extracting(CollectionsStockRow::currency).isEqualTo(Currency.USD);
    }

    @Test
    void thereIsAlwaysATotalRowPerCurrencyEvenWithoutDebtSoThatADayWithoutDebtIsNotADayWithoutPhoto() {
        List<CollectionsStockRow> rows = CollectionsStockCalculator.calculateStock(END_OF_MARCH, List.of());

        assertThat(rows).hasSize(Currency.values().length);
        for (Currency currency : Currency.values()) {
            CollectionsStockRow total = rowOf(rows, currency, CollectionsStockScope.TOTAL);
            assertThat(total.totalOverdue().isZero()).isTrue();
            assertThat(total.studentsWithOverdue()).isZero();
            assertThat(total.businessDate()).isEqualTo(END_OF_MARCH);
        }
    }

    @Test
    void anInvoiceIsCountedInEveryProgramAndCourseItHasLinesFor() {
        List<CollectionsStockRow> rows = CollectionsStockCalculator.calculateStock(END_OF_MARCH, List.of(
                fact(1, 1, 10, ars("1000.00"), Set.of(1L, 2L), Set.of(5L)), fact(2, 2, 10, ars("300.00"), Set.of(2L), Set.of())));

        assertThat(rowOf(rows, Currency.ARS, CollectionsStockScope.ofProgram(1L)).totalOverdue()).isEqualTo(ars("1000.00"));
        assertThat(rowOf(rows, Currency.ARS, CollectionsStockScope.ofProgram(2L)).totalOverdue()).isEqualTo(ars("1300.00"));
        assertThat(rowOf(rows, Currency.ARS, CollectionsStockScope.ofProgram(2L)).studentsWithOverdue()).isEqualTo(2);
        assertThat(rowOf(rows, Currency.ARS, CollectionsStockScope.ofCourse(5L)).totalOverdue()).isEqualTo(ars("1000.00"));
        assertThat(rowOf(rows, Currency.ARS, CollectionsStockScope.TOTAL).totalOverdue()).isEqualTo(ars("1300.00"));
    }

    @Test
    void anInvoiceWithoutBalanceIsNotDebtAndCreatesNoScopeRow() {
        List<CollectionsStockRow> rows = CollectionsStockCalculator.calculateStock(END_OF_MARCH, List.of(fact(1, 1, 10, ars("0.00"), Set.of(3L), Set.of())));

        assertThat(rows).hasSize(Currency.values().length);
        assertThat(rowOf(rows, Currency.ARS, CollectionsStockScope.TOTAL).studentsWithOverdue()).isZero();
    }

    @Test
    void aNegativeBalanceOrAForeignCurrencyBucketIsRejected() {
        assertThatThrownBy(() -> fact(1, 1, 10, ars("-1.00"), Set.of(), Set.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CollectionsStockRow(END_OF_MARCH, Currency.ARS, CollectionsStockScope.TOTAL, Map.of(OverdueAgeBucket.FROM_1_TO_30, usd("1.00")), 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CollectionsStockRow(END_OF_MARCH, Currency.ARS, CollectionsStockScope.TOTAL, Map.of(OverdueAgeBucket.FROM_1_TO_30, ars("-1.00")), 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CollectionsStockRow(END_OF_MARCH, Currency.ARS, CollectionsStockScope.TOTAL, Map.of(), -1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aScopeIsTheWholeInstituteOneProgramOrOneCourseButNeverBoth() {
        assertThat(CollectionsStockScope.TOTAL.isTotal()).isTrue();
        assertThat(CollectionsStockScope.ofProgram(1L).isTotal()).isFalse();
        assertThat(CollectionsStockScope.ofCourse(1L).isTotal()).isFalse();
        assertThatThrownBy(() -> new CollectionsStockScope(1L, 2L)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CollectionsStockScope.ofProgram(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> CollectionsStockScope.ofCourse(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void anInvoiceKnowsItsBucketOnAGivenDay() {
        OverdueInvoiceFact invoice = fact(1, 1, 30, ars("1.00"), Set.of(), Set.of());

        assertThat(invoice.bucketOn(END_OF_MARCH)).isEqualTo(OverdueAgeBucket.FROM_1_TO_30);
        assertThat(invoice.bucketOn(END_OF_MARCH.plusDays(1))).isEqualTo(OverdueAgeBucket.FROM_31_TO_60);
        assertThatThrownBy(() -> invoice.bucketOn(END_OF_MARCH.minusDays(30))).isInstanceOf(IllegalArgumentException.class);
    }
}
