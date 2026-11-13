package com.red_escuela.backend.attendance.api;

import com.red_escuela.backend.academicstructure.domain.SectionKind;
import com.red_escuela.backend.shared.domain.Percentage;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

@Component
@Transactional(readOnly = true)
public class SectionPresencePercentFinder {

    private final AttendanceStandingReader attendanceStandingReader;

    public SectionPresencePercentFinder(AttendanceStandingReader attendanceStandingReader) {
        this.attendanceStandingReader = attendanceStandingReader;
    }

    public Map<Long, Percentage> findPresencePercentByEnrollment(SectionKind kind, Long sectionId) {
        Map<Long, AttendanceStanding> standings = kind == SectionKind.PROGRAM
                ? attendanceStandingReader.readProgramSectionStandings(sectionId, null)
                : attendanceStandingReader.readCourseSectionStandings(sectionId, null);
        Map<Long, Percentage> presenceByEnrollment = new HashMap<>();
        standings.forEach((enrollmentId, standing) -> {
            if (standing.presencePercent() != null) {
                presenceByEnrollment.put(enrollmentId, standing.presencePercent());
            }
        });
        return presenceByEnrollment;
    }
}
