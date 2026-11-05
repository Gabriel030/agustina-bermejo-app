package com.red_escuela.backend.billing.discount.domain;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("D-282")
class ScholarshipPeriodRangeTest {
    private final ScholarshipPeriodRange marchToJune = new ScholarshipPeriodRange(YearMonth.of(2027, 3), YearMonth.of(2027, 6));

    @Test
    void countsMonthsAndOrdinalsInclusivelyAcrossAYearBoundary() {
        ScholarshipPeriodRange acrossYears = new ScholarshipPeriodRange(YearMonth.of(2027, 11), YearMonth.of(2028, 2));

        assertThat(marchToJune.monthCount()).isEqualTo(4);
        assertThat(acrossYears.monthCount()).isEqualTo(4);
        assertThat(marchToJune.ordinalOf(YearMonth.of(2027, 3))).isEqualTo(1);
        assertThat(marchToJune.ordinalOf(YearMonth.of(2027, 5))).isEqualTo(3);
        assertThat(marchToJune.ordinalOf(YearMonth.of(2027, 6))).isEqualTo(4);
        assertThat(acrossYears.ordinalOf(YearMonth.of(2028, 1))).isEqualTo(3);
    }

    @Test
    void containsTheBoundsAndNothingOutside() {
        assertThat(marchToJune.contains(YearMonth.of(2027, 3))).isTrue();
        assertThat(marchToJune.contains(YearMonth.of(2027, 6))).isTrue();
        assertThat(marchToJune.contains(YearMonth.of(2027, 2))).isFalse();
        assertThat(marchToJune.contains(YearMonth.of(2027, 7))).isFalse();
    }

    @Test
    void aSingleMonthRangeIsValidAndAnInvertedOneIsNot() {
        assertThat(new ScholarshipPeriodRange(YearMonth.of(2027, 4), YearMonth.of(2027, 4)).monthCount()).isEqualTo(1);
        assertThatThrownBy(() -> new ScholarshipPeriodRange(YearMonth.of(2027, 5), YearMonth.of(2027, 4))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> marchToJune.ordinalOf(YearMonth.of(2027, 7))).isInstanceOf(IllegalArgumentException.class);
    }
}
