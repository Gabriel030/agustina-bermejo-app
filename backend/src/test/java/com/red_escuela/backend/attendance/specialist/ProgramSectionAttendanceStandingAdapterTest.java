package com.red_escuela.backend.attendance.specialist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.red_escuela.backend.attendance.api.AttendanceStanding;
import com.red_escuela.backend.attendance.api.AttendanceStandingReader;
import com.red_escuela.backend.enrollment.api.SubjectAttendanceView;
import com.red_escuela.backend.shared.domain.Percentage;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ProgramSectionAttendanceStandingAdapterTest {

    private final AttendanceStandingReader reader = mock(AttendanceStandingReader.class);
    private final ProgramSectionAttendanceStandingAdapter adapter = new ProgramSectionAttendanceStandingAdapter(reader);

    @Test
    void exposesPresencePercentOnlyWhenTheStandingIsCalculable() {
        Percentage presence = Percentage.ofFraction(new BigDecimal("0.75"));
        when(reader.readProgramEnrollmentStandings(List.of(1L, 2L, 3L))).thenReturn(Map.of(
                1L, new AttendanceStanding(8, 6, 2, 0, Percentage.ofFraction(new BigDecimal("0.25")), presence,
                        Percentage.ofFraction(new BigDecimal("0.30")), false, 2, 0),
                2L, new AttendanceStanding(0, 0, 0, 0, null, presence, null, false, null, null),
                3L, new AttendanceStanding(0, 0, 0, 0, null, null, Percentage.ofFraction(new BigDecimal("0.30")), false, null, null)));

        Map<Long, SubjectAttendanceView> views = adapter.readProgramSectionAttendance(List.of(1L, 2L, 3L));

        assertThat(views.get(1L).presencePercent()).isEqualTo(presence);
        assertThat(views.get(2L).presencePercent()).isNull();
        assertThat(views.get(3L).presencePercent()).isNull();
    }
}
