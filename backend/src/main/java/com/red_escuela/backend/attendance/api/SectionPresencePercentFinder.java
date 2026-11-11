package com.red_escuela.backend.attendance.api;

import com.red_escuela.backend.academicstructure.domain.SectionKind;
import com.red_escuela.backend.attendance.entity.AttendanceStatus;
import com.red_escuela.backend.attendance.repository.AttendanceRepository;
import com.red_escuela.backend.attendance.repository.EnrollmentAttendanceCounts;
import com.red_escuela.backend.shared.domain.Percentage;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@Transactional(readOnly = true)
public class SectionPresencePercentFinder {

    private final AttendanceRepository attendanceRepository;

    public SectionPresencePercentFinder(AttendanceRepository attendanceRepository) {
        this.attendanceRepository = attendanceRepository;
    }

    public Map<Long, Percentage> findPresencePercentByEnrollment(SectionKind kind, Long sectionId) {
        List<EnrollmentAttendanceCounts> counts = kind == SectionKind.PROGRAM
                ? attendanceRepository.countsByProgramSection(
                        sectionId, AttendanceStatus.presenceStatuses(), AttendanceStatus.absenceStatuses())
                : attendanceRepository.countsByCourseSection(
                        sectionId, AttendanceStatus.presenceStatuses(), AttendanceStatus.absenceStatuses());
        Map<Long, Percentage> presenceByEnrollment = new HashMap<>();
        counts.forEach(count -> Percentage.ratio(count.getPresent() == null ? 0L : count.getPresent(), count.getTotal())
                .ifPresent(presence -> presenceByEnrollment.put(count.getEnrollmentId(), presence)));
        return presenceByEnrollment;
    }
}
