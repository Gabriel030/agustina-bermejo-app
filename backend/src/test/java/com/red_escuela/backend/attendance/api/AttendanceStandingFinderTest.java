package com.red_escuela.backend.attendance.api;

import com.red_escuela.backend.shared.domain.Percentage;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AttendanceStandingFinderTest {

    private final AttendanceStandingReader attendanceStandingReader = mock(AttendanceStandingReader.class);
    private final AttendanceStandingFinder finder = new AttendanceStandingFinder(attendanceStandingReader);

    @Test
    void programSectionStandingMapsCalculableAndFree() {
        AttendanceStanding risk = standing(true, true);
        when(attendanceStandingReader.readProgramEnrollmentStanding(7L)).thenReturn(risk);

        assertThat(finder.ofProgramSectionEnrollment(7L)).isEqualTo(new AttendanceStandingView(true, true));
    }

    @Test
    void courseSectionStandingMapsCalculableAndFree() {
        AttendanceStanding risk = standing(false, false);
        when(attendanceStandingReader.readCourseEnrollmentStanding(9L)).thenReturn(risk);

        assertThat(finder.ofCourseSectionEnrollment(9L)).isEqualTo(new AttendanceStandingView(false, false));
    }

    private AttendanceStanding standing(boolean calculable, boolean exceeds) {
        Percentage max = calculable ? Percentage.ofPoints(BigDecimal.valueOf(25)) : null;
        return new AttendanceStanding(8, 4, 3, 0, null, null, max, exceeds, null, null);
    }
}
