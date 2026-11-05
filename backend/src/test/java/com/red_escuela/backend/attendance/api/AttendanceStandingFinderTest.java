package com.red_escuela.backend.attendance.api;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AttendanceStandingFinderTest {

    private final AttendanceRiskReader attendanceRiskReader = mock(AttendanceRiskReader.class);
    private final AttendanceStandingFinder finder = new AttendanceStandingFinder(attendanceRiskReader);

    @Test
    void programSectionStandingMapsCalculableAndFree() {
        AttendanceRiskView risk = new AttendanceRiskView(true, true, null, null);
        when(attendanceRiskReader.readProgramEnrollmentRisk(7L)).thenReturn(risk);

        assertThat(finder.ofProgramSectionEnrollment(7L)).isEqualTo(new AttendanceStandingView(true, true));
    }

    @Test
    void courseSectionStandingMapsCalculableAndFree() {
        AttendanceRiskView risk = new AttendanceRiskView(false, false, null, null);
        when(attendanceRiskReader.readCourseEnrollmentRisk(9L)).thenReturn(risk);

        assertThat(finder.ofCourseSectionEnrollment(9L)).isEqualTo(new AttendanceStandingView(false, false));
    }
}
