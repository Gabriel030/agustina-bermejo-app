package com.red_escuela.backend.attendance.api;

import com.red_escuela.backend.attendance.entity.AttendanceStatus;
import com.red_escuela.backend.attendance.repository.AttendanceRepository;
import com.red_escuela.backend.enrollment.api.SectionEnrollmentFinder;
import com.red_escuela.backend.enrollment.entity.CourseSubjectSectionEnrollment;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.shared.domain.Percentage;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AttendanceRiskReaderTest {

    private final AttendanceRepository attendanceRepository = mock(AttendanceRepository.class);
    private final SectionEnrollmentFinder sectionEnrollmentFinder = mock(SectionEnrollmentFinder.class);
    private final AttendanceRiskReader reader = new AttendanceRiskReader(attendanceRepository, sectionEnrollmentFinder);
    private final Percentage maxTwentyFive = Percentage.ofPoints(BigDecimal.valueOf(25));

    @Test
    void programEnrollmentWithMoreAbsencesThanTheMaximumIsFree() {
        SubjectSectionEnrollment enrollment = mock(SubjectSectionEnrollment.class, RETURNS_DEEP_STUBS);
        when(enrollment.getSubjectSection().getMaxAbsenceRate()).thenReturn(maxTwentyFive);
        when(sectionEnrollmentFinder.findProgramEnrollment(7L)).thenReturn(enrollment);
        when(attendanceRepository.countByProgramSubjectSectionEnrollmentId(7L)).thenReturn(8L);
        when(attendanceRepository.countByProgramSubjectSectionEnrollmentIdAndStatusIn(eq(7L), any())).thenReturn(3L);

        AttendanceRiskView risk = reader.readProgramEnrollmentRisk(7L);

        assertThat(risk.libre()).isTrue();
        assertThat(risk.absencePercent().points()).isEqualByComparingTo("37.5");
    }

    @Test
    void courseEnrollmentWithAbsencesWithinTheMaximumIsNotFree() {
        CourseSubjectSectionEnrollment enrollment = mock(CourseSubjectSectionEnrollment.class, RETURNS_DEEP_STUBS);
        when(enrollment.getCourseSubjectSection().getMaxAbsenceRate()).thenReturn(maxTwentyFive);
        when(sectionEnrollmentFinder.findCourseEnrollment(9L)).thenReturn(enrollment);
        when(attendanceRepository.countByCourseSubjectSectionEnrollmentId(9L)).thenReturn(8L);
        when(attendanceRepository.countByCourseSubjectSectionEnrollmentIdAndStatusIn(9L, AttendanceStatus.absenceStatuses()))
                .thenReturn(2L);

        AttendanceRiskView risk = reader.readCourseEnrollmentRisk(9L);

        assertThat(risk.calculable()).isTrue();
        assertThat(risk.libre()).isFalse();
    }

    @Test
    void sectionWithoutMaximumIsNotCalculableAndSkipsCounting() {
        SubjectSectionEnrollment enrollment = mock(SubjectSectionEnrollment.class, RETURNS_DEEP_STUBS);
        when(enrollment.getSubjectSection().getMaxAbsenceRate()).thenReturn(null);
        when(sectionEnrollmentFinder.findProgramEnrollment(3L)).thenReturn(enrollment);

        AttendanceRiskView risk = reader.readProgramEnrollmentRisk(3L);

        assertThat(risk.calculable()).isFalse();
        assertThat(risk.libre()).isFalse();
        verifyNoInteractions(attendanceRepository);
    }
}
