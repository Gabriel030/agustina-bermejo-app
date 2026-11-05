package com.red_escuela.backend.attendance.api;

import com.red_escuela.backend.attendance.entity.AttendanceStatus;
import com.red_escuela.backend.attendance.repository.AttendanceRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SectionAbsenceCountsReaderTest {

    private final AttendanceRepository attendanceRepository = mock(AttendanceRepository.class);
    private final SectionAbsenceCountsReader reader = new SectionAbsenceCountsReader(attendanceRepository);

    @Test
    void mapsProgramRowsToTotalAndAbsentCountsPerEnrollment() {
        when(attendanceRepository.countsByProgramEnrollment(List.of(1L, 2L), AttendanceStatus.ABSENT))
                .thenReturn(List.of(new Object[]{1L, 10L, 3L}, new Object[]{2L, 4L, null}));

        Map<Long, EnrollmentAbsenceCounts> counts = reader.countByProgramEnrollments(List.of(1L, 2L));

        assertThat(counts).containsOnly(
                Map.entry(1L, new EnrollmentAbsenceCounts(10L, 3L)),
                Map.entry(2L, new EnrollmentAbsenceCounts(4L, 0L)));
    }

    @Test
    void mapsCourseRowsToTotalAndAbsentCountsPerEnrollment() {
        when(attendanceRepository.countsByCourseEnrollment(List.of(5L), AttendanceStatus.ABSENT))
                .thenReturn(List.<Object[]>of(new Object[]{5L, 8L, 2L}));

        assertThat(reader.countByCourseEnrollments(List.of(5L)))
                .containsOnly(Map.entry(5L, new EnrollmentAbsenceCounts(8L, 2L)));
    }

    @Test
    void returnsNoCountsWithoutQueryingWhenThereAreNoEnrollments() {
        assertThat(reader.countByProgramEnrollments(List.of())).isEmpty();
        assertThat(reader.countByCourseEnrollments(List.of())).isEmpty();

        verifyNoInteractions(attendanceRepository);
    }
}
