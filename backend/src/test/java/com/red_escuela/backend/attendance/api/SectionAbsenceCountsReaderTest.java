package com.red_escuela.backend.attendance.api;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SectionAbsenceCountsReaderTest {

    private final AttendanceStandingReader standingReader = mock(AttendanceStandingReader.class);
    private final SectionAbsenceCountsReader reader = new SectionAbsenceCountsReader(standingReader);

    @Test
    void b2U08ProgramNoticesCountThePendingJustificationAbsencesAsAbsences() {
        when(standingReader.readProgramEnrollmentCounts(List.of(1L, 2L))).thenReturn(Map.of(
                1L, standing(10, 3, 1),
                2L, standing(4, 0, 0)));

        Map<Long, EnrollmentAbsenceCountsView> counts = reader.countByProgramEnrollments(List.of(1L, 2L));

        assertThat(counts).containsOnly(
                Map.entry(1L, new EnrollmentAbsenceCountsView(10L, 3L, 1L)),
                Map.entry(2L, new EnrollmentAbsenceCountsView(4L, 0L, 0L)));
    }

    @Test
    void b2U09CourseNoticesUseTheSameCountAsTheReader() {
        when(standingReader.readCourseEnrollmentCounts(List.of(5L))).thenReturn(Map.of(5L, standing(8, 2, 2)));

        assertThat(reader.countByCourseEnrollments(List.of(5L)))
                .containsOnly(Map.entry(5L, new EnrollmentAbsenceCountsView(8L, 2L, 2L)));
    }

    @Test
    void returnsNoCountsWhenThereAreNoEnrollments() {
        when(standingReader.readProgramEnrollmentCounts(List.of())).thenReturn(Map.of());

        assertThat(reader.countByProgramEnrollments(List.of())).isEmpty();
    }

    private AttendanceStanding standing(long total, long absences, long pending) {
        return new AttendanceStanding(total, total - absences, absences, pending, null, null, null, false, null, null);
    }
}
