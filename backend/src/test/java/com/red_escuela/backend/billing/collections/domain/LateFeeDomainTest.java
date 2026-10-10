package com.red_escuela.backend.billing.collections.domain;

import com.red_escuela.backend.billing.invoice.domain.ChargeKey;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import com.red_escuela.backend.shared.domain.Percentage;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LateFeeDomainTest {
    private final MonthlySimpleLateFeeCalculator calculator = new MonthlySimpleLateFeeCalculator();

    private NonNegativeMoney ars(String amount) {
        return NonNegativeMoney.of(new BigDecimal(amount), Currency.ARS);
    }

    private BigDecimal lateFee(String price, String points) {
        return calculator.calculateLateFee(ars(price), Percentage.ofPoints(new BigDecimal(points))).value().amount();
    }

    @Test
    @Tag("D-099")
    void lateFeeRoundsHalfUpToTwoDecimals() {
        assertThat(lateFee("48000.00", "10")).isEqualByComparingTo("4800.00");
        assertThat(lateFee("80000.05", "5")).isEqualByComparingTo("4000.00");
        assertThat(lateFee("80010.10", "5")).isEqualByComparingTo("4000.51");
        assertThat(lateFee("80000.00", "0")).isEqualByComparingTo("0.00");
    }

    @Test
    @Tag("D-061")
    void factDatesAreComputedFromTheOriginWithoutDrift() {
        LateFeeSchedule schedule2027 = new LateFeeSchedule(LocalDate.of(2027, 1, 25), 5);
        assertThat(schedule2027.firstLateFeeDate()).isEqualTo(LocalDate.of(2027, 1, 31));
        assertThat(List.of(2, 3, 4).stream().map(o -> schedule2027.lateFeeMonth(o).factDate()).toList())
                .containsExactly(LocalDate.of(2027, 2, 28), LocalDate.of(2027, 3, 31), LocalDate.of(2027, 4, 30));
        LateFeeSchedule schedule2028 = new LateFeeSchedule(LocalDate.of(2028, 1, 25), 5);
        assertThat(List.of(2, 3, 4).stream().map(o -> schedule2028.lateFeeMonth(o).factDate()).toList())
                .containsExactly(LocalDate.of(2028, 2, 29), LocalDate.of(2028, 3, 31), LocalDate.of(2028, 4, 30));
    }

    @Test
    @Tag("D-061")
    void monthsWithFactDateOnOrBeforeIncludeTheLimitDayAndStartTheDayAfterGrace() {
        LateFeeSchedule schedule = new LateFeeSchedule(LocalDate.of(2027, 3, 12), 5);
        assertThat(schedule.monthsWithFactDateOnOrBefore(LocalDate.of(2027, 3, 17))).isEmpty();
        assertThat(schedule.monthsWithFactDateOnOrBefore(LocalDate.of(2027, 3, 18))).extracting(LateFeeMonth::ordinal).containsExactly(1);
        assertThat(schedule.monthsWithFactDateOnOrBefore(LocalDate.of(2027, 4, 17))).hasSize(1);
        assertThat(schedule.monthsWithFactDateOnOrBefore(LocalDate.of(2027, 4, 18))).extracting(LateFeeMonth::ordinal).containsExactly(1, 2);
        assertThat(schedule.monthsWithFactDateOnOrBefore(LocalDate.of(2028, 5, 18))).hasSize(15);
    }

    @Test
    @Tag("D-061")
    void invalidArgumentsAreRejected() {
        assertThatThrownBy(() -> new LateFeeSchedule(LocalDate.of(2027, 3, 12), -1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LateFeeSchedule(LocalDate.of(2027, 3, 12), 0).lateFeeMonth(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LateFeeMonth(0, LocalDate.of(2027, 3, 12))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ChargeKey.forLateFee(1L, 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @Tag("D-061")
    void chargeKeyIsBuiltByOriginAndMonthOrdinalNotByRunDate() {
        assertThat(ChargeKey.forLateFee(15L, 1).value()).isEqualTo("LF:15:M1");
        assertThat(ChargeKey.forLateFee(15L, 14).value()).isEqualTo("LF:15:M14");
    }
}
