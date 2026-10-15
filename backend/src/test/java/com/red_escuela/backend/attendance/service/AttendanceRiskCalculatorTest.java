package com.red_escuela.backend.attendance.service;

import com.red_escuela.backend.attendance.dto.AttendanceRiskResponse;
import com.red_escuela.backend.attendance.entity.AttendanceStatus;
import com.red_escuela.backend.shared.domain.Percentage;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class AttendanceRiskCalculatorTest {

    private final AttendanceRiskCalculator calculator = new AttendanceRiskCalculator(null, null, null);
    private final Percentage maxTwentyFive = Percentage.ofPoints(BigDecimal.valueOf(25));

    @Test
    void twoAbsencesOutOfEightWithAMaximumOfTwentyFivePercentIsNotFree() {
        AttendanceRiskResponse risk = calculator.evaluate(maxTwentyFive, 8, 2);

        assertThat(risk.calculable()).isTrue();
        assertThat(risk.libre()).isFalse();
        assertThat(risk.absencePercent().points()).isEqualByComparingTo("25");
        assertThat(risk.maxAbsencePercent().points()).isEqualByComparingTo("25");
    }

    @Test
    void threeAbsencesOutOfEightExceedsTheMaximumAndIsFree() {
        AttendanceRiskResponse risk = calculator.evaluate(maxTwentyFive, 8, 3);

        assertThat(risk.libre()).isTrue();
        assertThat(risk.absencePercent().points()).isEqualByComparingTo("37.5");
    }

    @Test
    void noClassesYetIsCalculableButHasNoPercentage() {
        AttendanceRiskResponse risk = calculator.evaluate(maxTwentyFive, 0, 0);

        assertThat(risk.calculable()).isTrue();
        assertThat(risk.libre()).isFalse();
        assertThat(risk.absencePercent()).isNull();
    }

    @Test
    void sectionWithoutMaximumIsNotCalculable() {
        AttendanceRiskResponse risk = calculator.evaluate(null, 8, 8);

        assertThat(risk.calculable()).isFalse();
        assertThat(risk.libre()).isFalse();
        assertThat(risk.maxAbsencePercent()).isNull();
    }

    @Test
    void onlyAbsentAndPendingJustificationCountAsAbsence() {
        assertThat(AttendanceStatus.absenceStatuses())
                .containsExactlyInAnyOrder(AttendanceStatus.ABSENT, AttendanceStatus.JUSTIFICATION_PENDING);
        assertThat(AttendanceStatus.JUSTIFIED.countsAsAbsence()).isFalse();
        assertThat(AttendanceStatus.JUSTIFIED.countsAsPresence()).isTrue();
        assertThat(AttendanceStatus.presenceStatuses())
                .containsExactlyInAnyOrder(AttendanceStatus.PRESENT, AttendanceStatus.LATE, AttendanceStatus.JUSTIFIED);
    }
}
