package com.red_escuela.backend.attendance.service;

import com.red_escuela.backend.attendance.dto.AttendanceRiskResponse;
import com.red_escuela.backend.attendance.entity.AttendanceStatus;
import com.red_escuela.backend.attendance.repository.AttendanceRepository;
import com.red_escuela.backend.enrollment.entity.CourseSubjectSectionEnrollment;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.enrollment.exception.SectionEnrollmentNotFoundException;
import com.red_escuela.backend.enrollment.repository.CourseSubjectSectionEnrollmentRepository;
import com.red_escuela.backend.enrollment.repository.SubjectSectionEnrollmentRepository;
import com.red_escuela.backend.shared.domain.Percentage;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class AttendanceRiskCalculator {

    private final AttendanceRepository attendanceRepository;
    private final SubjectSectionEnrollmentRepository subjectSectionEnrollmentRepository;
    private final CourseSubjectSectionEnrollmentRepository courseSubjectSectionEnrollmentRepository;

    public AttendanceRiskCalculator(AttendanceRepository attendanceRepository,
                                     SubjectSectionEnrollmentRepository subjectSectionEnrollmentRepository,
                                     CourseSubjectSectionEnrollmentRepository courseSubjectSectionEnrollmentRepository) {
        this.attendanceRepository = attendanceRepository;
        this.subjectSectionEnrollmentRepository = subjectSectionEnrollmentRepository;
        this.courseSubjectSectionEnrollmentRepository = courseSubjectSectionEnrollmentRepository;
    }

    public AttendanceRiskResponse calculateForProgramEnrollment(Long enrollmentId) {
        SubjectSectionEnrollment enrollment = subjectSectionEnrollmentRepository.findById(enrollmentId)
                .orElseThrow(() -> new SectionEnrollmentNotFoundException(enrollmentId));
        Percentage maxAbsenceRate = enrollment.getSubjectSection().getMaxAbsenceRate();
        if (maxAbsenceRate == null) {
            return evaluate(null, 0, 0);
        }
        long total = attendanceRepository.countByProgramSubjectSectionEnrollmentId(enrollmentId);
        long ausentes = attendanceRepository.countByProgramSubjectSectionEnrollmentIdAndStatusIn(
                enrollmentId, AttendanceStatus.absenceStatuses());
        return evaluate(maxAbsenceRate, total, ausentes);
    }

    public AttendanceRiskResponse calculateForCourseEnrollment(Long enrollmentId) {
        CourseSubjectSectionEnrollment enrollment = courseSubjectSectionEnrollmentRepository.findById(enrollmentId)
                .orElseThrow(() -> new SectionEnrollmentNotFoundException(enrollmentId));
        Percentage maxAbsenceRate = enrollment.getCourseSubjectSection().getMaxAbsenceRate();
        if (maxAbsenceRate == null) {
            return evaluate(null, 0, 0);
        }
        long total = attendanceRepository.countByCourseSubjectSectionEnrollmentId(enrollmentId);
        long ausentes = attendanceRepository.countByCourseSubjectSectionEnrollmentIdAndStatusIn(
                enrollmentId, AttendanceStatus.absenceStatuses());
        return evaluate(maxAbsenceRate, total, ausentes);
    }

    public AttendanceRiskResponse evaluate(Percentage maxAbsenceRate, long total, long absences) {
        if (maxAbsenceRate == null) {
            return new AttendanceRiskResponse(false, false, null, null);
        }
        Optional<Percentage> absenceRate = Percentage.ratio(absences, total);
        if (absenceRate.isEmpty()) {
            return new AttendanceRiskResponse(true, false, null, maxAbsenceRate);
        }
        return new AttendanceRiskResponse(true, absenceRate.get().exceeds(maxAbsenceRate), absenceRate.get(), maxAbsenceRate);
    }
}
