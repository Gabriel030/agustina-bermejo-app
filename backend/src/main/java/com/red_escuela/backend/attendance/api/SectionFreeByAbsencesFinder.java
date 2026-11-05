package com.red_escuela.backend.attendance.api;

import com.red_escuela.backend.attendance.entity.AttendanceStatus;
import com.red_escuela.backend.attendance.repository.AttendanceRepository;
import com.red_escuela.backend.attendance.repository.EnrollmentAttendanceCounts;
import com.red_escuela.backend.shared.domain.Percentage;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@Transactional(readOnly = true)
public class SectionFreeByAbsencesFinder {

    private final AttendanceRepository attendanceRepository;
    private final AttendanceRiskReader attendanceRiskReader;

    public SectionFreeByAbsencesFinder(AttendanceRepository attendanceRepository,
                                       AttendanceRiskReader attendanceRiskReader) {
        this.attendanceRepository = attendanceRepository;
        this.attendanceRiskReader = attendanceRiskReader;
    }

    public Set<Long> findProgramEnrollmentIdsFreeByAbsences(Long sectionId, Percentage maxAbsenceRate) {
        if (maxAbsenceRate == null) {
            return Set.of();
        }
        return freeEnrollmentIds(attendanceRepository.countsByProgramSection(
                sectionId, AttendanceStatus.presenceStatuses(), AttendanceStatus.absenceStatuses()), maxAbsenceRate);
    }

    public Set<Long> findCourseEnrollmentIdsFreeByAbsences(Long sectionId, Percentage maxAbsenceRate) {
        if (maxAbsenceRate == null) {
            return Set.of();
        }
        return freeEnrollmentIds(attendanceRepository.countsByCourseSection(
                sectionId, AttendanceStatus.presenceStatuses(), AttendanceStatus.absenceStatuses()), maxAbsenceRate);
    }

    private Set<Long> freeEnrollmentIds(List<EnrollmentAttendanceCounts> counts, Percentage maxAbsenceRate) {
        return counts.stream()
                .filter(count -> attendanceRiskReader
                        .evaluateRisk(maxAbsenceRate, count.getTotal(), count.getAbsences() == null ? 0L : count.getAbsences())
                        .libre())
                .map(EnrollmentAttendanceCounts::getEnrollmentId)
                .collect(Collectors.toSet());
    }
}
