package com.red_escuela.backend.academicrecord.usecase.studentrecord;

import com.red_escuela.backend.academicrecord.dto.StudentGradeItemResponse;
import com.red_escuela.backend.academicrecord.service.AcademicRecordEnrollmentOwnership;
import com.red_escuela.backend.academicrecord.service.AcademicRecordGradeItemFinder;
import com.red_escuela.backend.shared.UseCase;

import java.util.List;

@UseCase
public class ListMyCourseEnrollmentGradeItemsService implements ListMyCourseEnrollmentGradeItemsUseCase {

    private final AcademicRecordEnrollmentOwnership enrollmentOwnership;
    private final AcademicRecordGradeItemFinder gradeItemFinder;

    public ListMyCourseEnrollmentGradeItemsService(
            AcademicRecordEnrollmentOwnership enrollmentOwnership, AcademicRecordGradeItemFinder gradeItemFinder) {
        this.enrollmentOwnership = enrollmentOwnership;
        this.gradeItemFinder = gradeItemFinder;
    }

    @Override
    public List<StudentGradeItemResponse> list(Long studentId, Long enrollmentId) {
        enrollmentOwnership.requireCourseEnrollmentOf(enrollmentId, studentId);
        return gradeItemFinder.listCourseEnrollmentGradeItems(enrollmentId);
    }
}
