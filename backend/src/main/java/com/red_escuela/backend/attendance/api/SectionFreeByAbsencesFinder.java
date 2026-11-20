package com.red_escuela.backend.attendance.api;

import com.red_escuela.backend.shared.domain.Percentage;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@Transactional(readOnly = true)
public class SectionFreeByAbsencesFinder {

    private final AttendanceStandingReader attendanceStandingReader;

    public SectionFreeByAbsencesFinder(AttendanceStandingReader attendanceStandingReader) {
        this.attendanceStandingReader = attendanceStandingReader;
    }

    public Set<Long> findProgramEnrollmentIdsFreeByAbsences(Long sectionId, Percentage maxAbsenceRate) {
        if (maxAbsenceRate == null) {
            return Set.of();
        }
        return freeEnrollmentIds(attendanceStandingReader.readProgramSectionStandings(sectionId, maxAbsenceRate));
    }

    public Set<Long> findCourseEnrollmentIdsFreeByAbsences(Long sectionId, Percentage maxAbsenceRate) {
        if (maxAbsenceRate == null) {
            return Set.of();
        }
        return freeEnrollmentIds(attendanceStandingReader.readCourseSectionStandings(sectionId, maxAbsenceRate));
    }

    private Set<Long> freeEnrollmentIds(Map<Long, AttendanceStanding> standings) {
        return standings.entrySet().stream()
                .filter(entry -> entry.getValue().exceedsAbsenceLimit())
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }
}
