package com.red_escuela.backend.attendance.api;

import com.red_escuela.backend.shared.domain.Percentage;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class AttendancePercentFormatTest {

    private final Percentage maxTwentyFive = Percentage.ofPoints(BigDecimal.valueOf(25));

    @Test
    void b2U10AbsencePercentIsShownWithOneDecimal() {
        assertThat(AttendancePercentFormat.absence(Percentage.ratio(3, 8).orElseThrow(), maxTwentyFive, true))
                .isEqualTo("37.5%");
        assertThat(AttendancePercentFormat.absence(Percentage.ratio(21, 80).orElseThrow(), maxTwentyFive, true))
                .isEqualTo("26.3%");
        assertThat(AttendancePercentFormat.plain(Percentage.ratio(1, 3).orElseThrow())).isEqualTo("33.3%");
        assertThat(AttendancePercentFormat.plain(null)).isEqualTo("-");
    }

    @Test
    void b2U11ExportedPercentNeverFallsOnOrBelowTheMaximumWhenTheDecisionIsFree() {
        for (long total = 1; total <= 400; total++) {
            for (long absences = 0; absences <= total; absences++) {
                Percentage absence = Percentage.ratio(absences, total).orElseThrow();
                boolean exceeds = absence.exceeds(maxTwentyFive);
                BigDecimal shown = shownPoints(AttendancePercentFormat.absence(absence, maxTwentyFive, exceeds));
                if (exceeds) {
                    assertThat(shown).as("%d/%d is free", absences, total).isGreaterThan(maxTwentyFive.points());
                } else {
                    assertThat(shown).as("%d/%d is not free", absences, total).isLessThanOrEqualTo(maxTwentyFive.points());
                }
            }
        }
    }

    @Test
    void b2U12JustOverTheMaximumIsRoundedUpInsteadOfShowingTheMaximum() {
        Percentage justOver = Percentage.ofFraction(new BigDecimal("0.250400"));

        assertThat(justOver.exceeds(maxTwentyFive)).isTrue();
        assertThat(AttendancePercentFormat.absence(justOver, maxTwentyFive, true)).isEqualTo("25.1%");
    }

    @Test
    void b2U13JustUnderTheMaximumIsNotRoundedAboveIt() {
        Percentage maxWithDecimals = Percentage.ofFraction(new BigDecimal("0.259600"));
        Percentage atTheMaximum = Percentage.ofFraction(new BigDecimal("0.259600"));

        assertThat(atTheMaximum.exceeds(maxWithDecimals)).isFalse();
        assertThat(AttendancePercentFormat.absence(atTheMaximum, maxWithDecimals, false)).isEqualTo("25.9%");
    }

    private BigDecimal shownPoints(String label) {
        return new BigDecimal(label.substring(0, label.length() - 1));
    }
}
