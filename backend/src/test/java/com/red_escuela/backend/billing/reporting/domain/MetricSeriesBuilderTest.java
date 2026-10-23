package com.red_escuela.backend.billing.reporting.domain;

import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.Money;
import com.red_escuela.backend.shared.domain.Percentage;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MetricSeriesBuilderTest {

    private static final MonthRange RANGE = new MonthRange(YearMonth.of(2026, 3), YearMonth.of(2026, 5));

    private final MetricSeriesBuilder builder = new MetricSeriesBuilder(RANGE, Currency.USD);

    @Test
    void monthsWithoutActivityAreZeroForCountsAndMoneyAndEmptyForPercentagesAndDays() {
        builder.declare("count", MetricUnit.COUNT).declare("amount", MetricUnit.MONEY).declare("percent", MetricUnit.PERCENT).declare("days", MetricUnit.DAYS);

        List<MetricSeries> series = builder.build();

        assertThat(series.get(0).values()).containsExactly(new CountValue(0), new CountValue(0), new CountValue(0));
        assertThat(series.get(1).values()).containsExactly(new MoneyValue(Money.zero(Currency.USD)), new MoneyValue(Money.zero(Currency.USD)),
                new MoneyValue(Money.zero(Currency.USD)));
        assertThat(series.get(2).values()).containsExactly(null, null, null);
        assertThat(series.get(3).values()).containsExactly(null, null, null);
    }

    @Test
    void aSeriesDeclaredToLeaveMonthsWithoutDataEmptyKeepsThemNullEvenForMoneyAndCounts() {
        builder.declareLeavingMonthsWithoutDataEmpty("amount", MetricUnit.MONEY).declareLeavingMonthsWithoutDataEmpty("count", MetricUnit.COUNT).declare("other", MetricUnit.COUNT);
        builder.put("amount", YearMonth.of(2026, 4), new MoneyValue(Money.zero(Currency.USD)));

        List<MetricSeries> series = builder.build();

        assertThat(series.get(0).values()).containsExactly(null, new MoneyValue(Money.zero(Currency.USD)), null);
        assertThat(series.get(1).values()).containsExactly(null, null, null);
        assertThat(series.get(2).values()).containsExactly(new CountValue(0), new CountValue(0), new CountValue(0));
    }

    @Test
    void valuesLandOnTheirMonthAndSeriesKeepTheirDeclarationOrder() {
        builder.declare("first", MetricUnit.COUNT);
        builder.put("second", YearMonth.of(2026, 4), new PercentValue(Percentage.ofPoints(new BigDecimal("80"))));
        builder.put("first", YearMonth.of(2026, 5), new CountValue(7));

        List<MetricSeries> series = builder.build();

        assertThat(series).extracting(MetricSeries::key).containsExactly("first", "second");
        assertThat(series.get(0).values()).containsExactly(new CountValue(0), new CountValue(0), new CountValue(7));
        assertThat(series.get(1).values()).containsExactly(null, new PercentValue(Percentage.ofPoints(new BigDecimal("80"))), null);
    }

    @Test
    @Tag("D-097")
    void moneyOfTheSeriesCurrencyFillsTheEmptyMonths() {
        builder.put("amount", YearMonth.of(2026, 3), new MoneyValue(Money.of(new BigDecimal("100.00"), Currency.USD)));

        assertThat(builder.build().get(0).values().get(1)).isEqualTo(new MoneyValue(Money.zero(Currency.USD)));
    }

    @Test
    void valuesOutsideTheRangeAreIgnoredWithoutDeclaringTheSeries() {
        builder.put("count", YearMonth.of(2026, 6), new CountValue(1));
        builder.put("count", YearMonth.of(2026, 2), new CountValue(1));

        assertThat(builder.build()).isEmpty();
    }

    @Test
    void declaringAKeyAgainWithAnotherUnitIsRejected() {
        builder.declare("count", MetricUnit.COUNT);

        assertThatThrownBy(() -> builder.declare("count", MetricUnit.MONEY)).isInstanceOf(IllegalArgumentException.class);
        builder.declare("count", MetricUnit.COUNT);
    }

    @Test
    void aSeriesRejectsValuesOfAnotherUnit() {
        assertThatThrownBy(() -> new MetricSeries("count", MetricUnit.COUNT, List.of(new DaysValue(BigDecimal.ONE)))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void valueObjectsRejectNegativeQuantities() {
        assertThatThrownBy(() -> new CountValue(-1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DaysValue(new BigDecimal("-0.01"))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void daysAreRoundedToTwoDecimalsAndExposeTheirMagnitude() {
        assertThat(new DaysValue(new BigDecimal("2.345")).days()).isEqualByComparingTo("2.35");
        assertThat(new DaysValue(new BigDecimal("2")).magnitude().scale()).isEqualTo(2);
        assertThat(new CountValue(4).magnitude()).isEqualByComparingTo("4");
        assertThat(new MoneyValue(Money.of(new BigDecimal("12.5"), Currency.ARS)).magnitude()).isEqualByComparingTo("12.50");
        assertThat(new PercentValue(Percentage.ofPoints(new BigDecimal("45"))).magnitude()).isEqualByComparingTo("0.45");
    }

    @Test
    void metricFilterExposesItsOptionalCriteria() {
        MetricFilter open = new MetricFilter(RANGE, Currency.ARS, null, null, null);
        MetricFilter narrowed = new MetricFilter(RANGE, Currency.ARS, 4L, 9L, com.red_escuela.backend.billing.invoice.domain.InvoiceType.MONTHLY_INSTALLMENT);

        assertThat(open.program()).isEmpty();
        assertThat(open.course()).isEmpty();
        assertThat(open.type()).isEmpty();
        assertThat(narrowed.program()).contains(4L);
        assertThat(narrowed.course()).contains(9L);
        assertThat(narrowed.type()).contains(com.red_escuela.backend.billing.invoice.domain.InvoiceType.MONTHLY_INSTALLMENT);
    }
}
