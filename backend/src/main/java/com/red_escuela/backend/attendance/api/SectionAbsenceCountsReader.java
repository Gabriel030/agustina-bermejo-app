package com.red_escuela.backend.attendance.api;

import com.red_escuela.backend.attendance.entity.AttendanceStatus;
import com.red_escuela.backend.attendance.repository.AttendanceRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@Transactional(readOnly = true)
public class SectionAbsenceCountsReader {

    private final AttendanceRepository attendanceRepository;

    public SectionAbsenceCountsReader(AttendanceRepository attendanceRepository) {
        this.attendanceRepository = attendanceRepository;
    }

    public Map<Long, EnrollmentAbsenceCounts> countByProgramEnrollments(List<Long> enrollmentIds) {
        if (enrollmentIds.isEmpty()) {
            return Map.of();
        }
        return toCounts(attendanceRepository.countsByProgramEnrollment(enrollmentIds, AttendanceStatus.ABSENT));
    }

    public Map<Long, EnrollmentAbsenceCounts> countByCourseEnrollments(List<Long> enrollmentIds) {
        if (enrollmentIds.isEmpty()) {
            return Map.of();
        }
        return toCounts(attendanceRepository.countsByCourseEnrollment(enrollmentIds, AttendanceStatus.ABSENT));
    }

    private Map<Long, EnrollmentAbsenceCounts> toCounts(List<Object[]> rows) {
        Map<Long, EnrollmentAbsenceCounts> byEnrollment = new HashMap<>();
        for (Object[] row : rows) {
            Long enrollmentId = ((Number) row[0]).longValue();
            long total = ((Number) row[1]).longValue();
            long absent = row[2] == null ? 0L : ((Number) row[2]).longValue();
            byEnrollment.put(enrollmentId, new EnrollmentAbsenceCounts(total, absent));
        }
        return byEnrollment;
    }
}
