package com.red_escuela.backend.academicrecord.usecase;

import com.red_escuela.backend.academicrecord.dto.StudentCourseSectionEnrollmentResponse;
import com.red_escuela.backend.academicrecord.specialist.AcademicRecordCourseSectionEnrollmentFinder;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class ReadMyCourseSectionEnrollmentService implements ReadMyCourseSectionEnrollmentUseCase {

    private final AcademicRecordCourseSectionEnrollmentFinder courseSectionEnrollmentFinder;

    public ReadMyCourseSectionEnrollmentService(AcademicRecordCourseSectionEnrollmentFinder courseSectionEnrollmentFinder) {
        this.courseSectionEnrollmentFinder = courseSectionEnrollmentFinder;
    }

    @Override
    public StudentCourseSectionEnrollmentResponse read(Long studentId, Long id) {
        return courseSectionEnrollmentFinder.readOfStudent(id, studentId);
    }
}
