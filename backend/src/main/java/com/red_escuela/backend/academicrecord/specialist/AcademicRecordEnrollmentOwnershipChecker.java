package com.red_escuela.backend.academicrecord.specialist;

import com.red_escuela.backend.enrollment.api.SectionEnrollmentFinder;
import org.springframework.stereotype.Service;

@Service
public class AcademicRecordEnrollmentOwnershipChecker {

    private final SectionEnrollmentFinder sectionEnrollmentFinder;

    public AcademicRecordEnrollmentOwnershipChecker(SectionEnrollmentFinder sectionEnrollmentFinder) {
        this.sectionEnrollmentFinder = sectionEnrollmentFinder;
    }

    public void requireProgramEnrollmentOf(Long enrollmentId, Long studentId) {
        sectionEnrollmentFinder.requireProgramEnrollmentOf(enrollmentId, studentId);
    }

    public void requireCourseEnrollmentOf(Long enrollmentId, Long studentId) {
        sectionEnrollmentFinder.requireCourseEnrollmentOf(enrollmentId, studentId);
    }
}
