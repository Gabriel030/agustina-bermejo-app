package com.red_escuela.backend.billing.collections.domain;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("D-339")
class OverdueContactCriteriaTest {

    private final OverdueContactCriteria criteria = new OverdueContactCriteria(14, 2);

    @ParameterizedTest
    @CsvSource({"13,1,false", "14,1,true", "15,1,true", "1,2,true", "1,3,true", "13,2,true", "0,0,false"})
    void aStudentMeetsTheCriteriaWithTheAgeOfTheOldestDebtOrWithTheInstallmentsOverdue(int oldestDaysOverdue, int installments, boolean expected) {
        assertThat(criteria.isMetBy(oldestDaysOverdue, installments)).isEqualTo(expected);
    }

    @Test
    void theThresholdsAreTheOnesGivenNotTheDefaults() {
        OverdueContactCriteria stricter = new OverdueContactCriteria(30, 4);

        assertThat(stricter.isMetBy(29, 3)).isFalse();
        assertThat(stricter.isMetBy(30, 1)).isTrue();
        assertThat(stricter.isMetBy(1, 4)).isTrue();
    }

    @Test
    void thresholdsBelowOneAreRejected() {
        assertThatThrownBy(() -> new OverdueContactCriteria(0, 2)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new OverdueContactCriteria(14, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThat(new OverdueContactCriteria(1, 1).isMetBy(1, 1)).isTrue();
    }
}
