package com.red_escuela.backend.attendance.specialist;

import com.red_escuela.backend.attendance.api.AttendanceStanding;
import com.red_escuela.backend.attendance.api.AttendanceStandingReader;
import com.red_escuela.backend.attendance.dto.AttendanceRiskResponse;
import com.red_escuela.backend.attendance.entity.AttendanceStatus;
import com.red_escuela.backend.shared.domain.Percentage;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AttendanceRiskCalculatorTest {

    private final AttendanceStandingReader reader = mock(AttendanceStandingReader.class);
    private final AttendanceRiskCalculator calculator = new AttendanceRiskCalculator(reader);
    private final Percentage maxTwentyFive = Percentage.ofPoints(BigDecimal.valueOf(25));

    @Test
    void programStandingExceedingTheLimitIsReportedAsFree() {
        when(reader.readProgramEnrollmentStanding(1L)).thenReturn(new AttendanceStanding(
                8, 5, 3, 0, Percentage.ofPoints(BigDecimal.valueOf(37.5)), null, maxTwentyFive, true, 2, 0));

        AttendanceRiskResponse risk = calculator.calculateForProgramEnrollment(1L);

        assertThat(risk.calculable()).isTrue();
        assertThat(risk.libre()).isTrue();
        assertThat(risk.absencePercent().points()).isEqualByComparingTo("37.5");
        assertThat(risk.maxAbsencePercent().points()).isEqualByComparingTo("25");
    }

    @Test
    void courseStandingWithoutMaximumIsNotCalculableAndHidesThePercentage() {
        when(reader.readCourseEnrollmentStanding(2L)).thenReturn(new AttendanceStanding(
                8, 0, 8, 0, Percentage.ofPoints(BigDecimal.valueOf(100)), null, null, false, null, null));

        AttendanceRiskResponse risk = calculator.calculateForCourseEnrollment(2L);

        assertThat(risk.calculable()).isFalse();
        assertThat(risk.libre()).isFalse();
        assertThat(risk.absencePercent()).isNull();
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
