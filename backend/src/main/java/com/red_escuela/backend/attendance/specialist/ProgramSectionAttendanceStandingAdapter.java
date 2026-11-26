package com.red_escuela.backend.attendance.specialist;

import com.red_escuela.backend.attendance.api.AttendanceStanding;
import com.red_escuela.backend.attendance.api.AttendanceStandingReader;
import com.red_escuela.backend.enrollment.api.AttendanceStandingPort;
import com.red_escuela.backend.enrollment.api.SubjectAttendanceView;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class ProgramSectionAttendanceStandingAdapter implements AttendanceStandingPort {

    private final AttendanceStandingReader attendanceStandingReader;

    public ProgramSectionAttendanceStandingAdapter(AttendanceStandingReader attendanceStandingReader) {
        this.attendanceStandingReader = attendanceStandingReader;
    }

    @Override
    public Map<Long, SubjectAttendanceView> readProgramSectionAttendance(Collection<Long> subjectSectionEnrollmentIds) {
        Map<Long, SubjectAttendanceView> views = new HashMap<>();
        attendanceStandingReader.readProgramEnrollmentStandings(subjectSectionEnrollmentIds)
                .forEach((id, standing) -> views.put(id, toView(standing)));
        return views;
    }

    private SubjectAttendanceView toView(AttendanceStanding standing) {
        return new SubjectAttendanceView(standing.calculable() ? standing.presencePercent() : null);
    }
}
