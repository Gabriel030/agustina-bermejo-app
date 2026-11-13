package com.red_escuela.backend.attendance.specialist;

import com.red_escuela.backend.attendance.api.AttendanceStanding;
import com.red_escuela.backend.attendance.api.AttendanceStandingReader;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProgramSectionAbsenceLimitCheckerTest {

    private final AttendanceStandingReader reader = mock(AttendanceStandingReader.class);
    private final ProgramSectionAbsenceLimitChecker checker = new ProgramSectionAbsenceLimitChecker(reader);

    @Test
    void answersWithTheSameDecisionAsTheStandingReaderForEachEnrollment() {
        when(reader.readProgramEnrollmentStanding(1L)).thenReturn(standing(false));
        when(reader.readProgramEnrollmentStanding(2L)).thenReturn(standing(true));

        assertThat(checker.exceedsAbsenceLimit(1L)).isFalse();
        assertThat(checker.exceedsAbsenceLimit(2L)).isTrue();
    }

    private AttendanceStanding standing(boolean exceeds) {
        return new AttendanceStanding(8, 4, 4, 0, null, null, null, exceeds, null, null);
    }
}
