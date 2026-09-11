package com.red_escuela.backend.attendance.service;

import com.red_escuela.backend.attendance.dto.AttendanceRiskResponse;
import com.red_escuela.backend.attendance.entity.AttendanceStatus;
import com.red_escuela.backend.attendance.repository.AttendanceRepository;
import com.red_escuela.backend.enrollment.entity.CourseSubjectSectionEnrollment;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.enrollment.exception.SectionEnrollmentNotFoundException;
import com.red_escuela.backend.enrollment.repository.CourseSubjectSectionEnrollmentRepository;
import com.red_escuela.backend.enrollment.repository.SubjectSectionEnrollmentRepository;
import org.springframework.stereotype.Service;

import java.util.List;

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
        Double maxAbsencePercentage = enrollment.getSubjectSection().getMaxAbsencePercentage();
        if (maxAbsencePercentage == null) {
            return new AttendanceRiskResponse(false, false, null, null);
        }
        long total = attendanceRepository.countByProgramSubjectSectionEnrollmentId(enrollmentId);
        long ausentes = attendanceRepository.countByProgramSubjectSectionEnrollmentIdAndStatusIn(
                enrollmentId, List.of(AttendanceStatus.ABSENT, AttendanceStatus.JUSTIFICATION_PENDING));
        return calcularRiesgo(total, ausentes, maxAbsencePercentage);
    }

    public AttendanceRiskResponse calculateForCourseEnrollment(Long enrollmentId) {
        CourseSubjectSectionEnrollment enrollment = courseSubjectSectionEnrollmentRepository.findById(enrollmentId)
                .orElseThrow(() -> new SectionEnrollmentNotFoundException(enrollmentId));
        Double maxAbsencePercentage = enrollment.getCourseSubjectSection().getMaxAbsencePercentage();
        if (maxAbsencePercentage == null) {
            return new AttendanceRiskResponse(false, false, null, null);
        }
        long total = attendanceRepository.countByCourseSubjectSectionEnrollmentId(enrollmentId);
        long ausentes = attendanceRepository.countByCourseSubjectSectionEnrollmentIdAndStatusIn(
                enrollmentId, List.of(AttendanceStatus.ABSENT, AttendanceStatus.JUSTIFICATION_PENDING));
        return calcularRiesgo(total, ausentes, maxAbsencePercentage);
    }

    private AttendanceRiskResponse calcularRiesgo(long total, long ausentes, double maxAbsencePercentage) {
        if (total == 0) {
            return new AttendanceRiskResponse(true, false, 0.0, maxAbsencePercentage);
        }
        double porcentaje = (double) ausentes / total;
        return new AttendanceRiskResponse(true, porcentaje > maxAbsencePercentage, porcentaje, maxAbsencePercentage);
    }
}
