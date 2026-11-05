package com.red_escuela.backend.academicrecord.usecase.studentrecord;

import com.red_escuela.backend.academicrecord.dto.StudentCourseSectionEnrollmentResponse;
import com.red_escuela.backend.academicrecord.service.AcademicRecordCourseSectionEnrollmentFinder;
import com.red_escuela.backend.shared.UseCase;

import java.util.List;

@UseCase
public class ListMyCourseSectionEnrollmentsService implements ListMyCourseSectionEnrollmentsUseCase {

    private final AcademicRecordCourseSectionEnrollmentFinder courseSectionEnrollmentFinder;

    public ListMyCourseSectionEnrollmentsService(AcademicRecordCourseSectionEnrollmentFinder courseSectionEnrollmentFinder) {
        this.courseSectionEnrollmentFinder = courseSectionEnrollmentFinder;
    }

    @Override
    public List<StudentCourseSectionEnrollmentResponse> list(Long studentId, Long courseEnrollmentId) {
        return courseSectionEnrollmentFinder.listOfStudent(studentId, courseEnrollmentId);
    }
}
