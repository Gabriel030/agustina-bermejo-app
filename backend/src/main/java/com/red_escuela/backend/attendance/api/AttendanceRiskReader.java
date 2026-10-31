package com.red_escuela.backend.attendance.api;

import com.red_escuela.backend.attendance.entity.AttendanceStatus;
import com.red_escuela.backend.attendance.repository.AttendanceRepository;
import com.red_escuela.backend.enrollment.api.SectionEnrollmentFinder;
import com.red_escuela.backend.enrollment.entity.CourseSubjectSectionEnrollment;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.shared.domain.Percentage;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Component
@Transactional(readOnly = true)
public class AttendanceRiskReader {

    private final AttendanceRepository attendanceRepository;
    private final SectionEnrollmentFinder sectionEnrollmentFinder;

    public AttendanceRiskReader(AttendanceRepository attendanceRepository,
                                SectionEnrollmentFinder sectionEnrollmentFinder) {
        this.attendanceRepository = attendanceRepository;
        this.sectionEnrollmentFinder = sectionEnrollmentFinder;
    }

    public AttendanceRiskView readProgramEnrollmentRisk(Long enrollmentId) {
        SubjectSectionEnrollment enrollment = sectionEnrollmentFinder.findProgramEnrollment(enrollmentId);
        Percentage maxAbsenceRate = enrollment.getSubjectSection().getMaxAbsenceRate();
        if (maxAbsenceRate == null) {
            return evaluateRisk(null, 0, 0);
        }
        long total = attendanceRepository.countByProgramSubjectSectionEnrollmentId(enrollmentId);
        long absences = attendanceRepository.countByProgramSubjectSectionEnrollmentIdAndStatusIn(
                enrollmentId, AttendanceStatus.absenceStatuses());
        return evaluateRisk(maxAbsenceRate, total, absences);
    }

    public AttendanceRiskView readCourseEnrollmentRisk(Long enrollmentId) {
        CourseSubjectSectionEnrollment enrollment = sectionEnrollmentFinder.findCourseEnrollment(enrollmentId);
        Percentage maxAbsenceRate = enrollment.getCourseSubjectSection().getMaxAbsenceRate();
        if (maxAbsenceRate == null) {
            return evaluateRisk(null, 0, 0);
        }
        long total = attendanceRepository.countByCourseSubjectSectionEnrollmentId(enrollmentId);
        long absences = attendanceRepository.countByCourseSubjectSectionEnrollmentIdAndStatusIn(
                enrollmentId, AttendanceStatus.absenceStatuses());
        return evaluateRisk(maxAbsenceRate, total, absences);
    }

    public AttendanceRiskView evaluateRisk(Percentage maxAbsenceRate, long total, long absences) {
        if (maxAbsenceRate == null) {
            return new AttendanceRiskView(false, false, null, null);
        }
        Optional<Percentage> absenceRate = Percentage.ratio(absences, total);
        if (absenceRate.isEmpty()) {
            return new AttendanceRiskView(true, false, null, maxAbsenceRate);
        }
        return new AttendanceRiskView(true, absenceRate.get().exceeds(maxAbsenceRate), absenceRate.get(), maxAbsenceRate);
    }
}
