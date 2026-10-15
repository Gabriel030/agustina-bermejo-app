package com.red_escuela.backend.billing.reporting.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BusinessDaysTest {

    private static final LocalDate MONDAY = LocalDate.of(2026, 3, 2);

    @Test
    void sameDayHasNoElapsedBusinessDays() {
        assertThat(BusinessDays.elapsedBetween(MONDAY, MONDAY)).isZero();
    }

    @Test
    void mondayToThursdayAreThreeBusinessDays() {
        assertThat(BusinessDays.elapsedBetween(MONDAY, MONDAY.plusDays(3))).isEqualTo(3);
    }

    @Test
    void mondayToTheFollowingFridayAreFourBusinessDays() {
        assertThat(BusinessDays.elapsedBetween(MONDAY, MONDAY.plusDays(4))).isEqualTo(4);
    }

    @Test
    void theWeekendDoesNotCount() {
        LocalDate friday = MONDAY.plusDays(4);

        assertThat(BusinessDays.elapsedBetween(friday, friday.plusDays(1))).isZero();
        assertThat(BusinessDays.elapsedBetween(friday, friday.plusDays(2))).isZero();
        assertThat(BusinessDays.elapsedBetween(friday, friday.plusDays(3))).isEqualTo(1);
    }

    @Test
    void aFullWeekIsFiveBusinessDays() {
        assertThat(BusinessDays.elapsedBetween(MONDAY, MONDAY.plusDays(7))).isEqualTo(5);
    }

    @Test
    void anEndBeforeTheStartIsRejected() {
        assertThatThrownBy(() -> BusinessDays.elapsedBetween(MONDAY, MONDAY.minusDays(1))).isInstanceOf(IllegalArgumentException.class);
    }
}
