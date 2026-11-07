package com.red_escuela.backend.academicrecord.usecase;

import com.red_escuela.backend.academicrecord.dto.StudentGradeItemResponse;
import com.red_escuela.backend.academicrecord.specialist.AcademicRecordEnrollmentOwnershipChecker;
import com.red_escuela.backend.academicrecord.specialist.AcademicRecordGradeItemFinder;
import com.red_escuela.backend.shared.UseCase;

import java.util.List;

@UseCase
public class ListMyCourseEnrollmentGradeItemsService implements ListMyCourseEnrollmentGradeItemsUseCase {

    private final AcademicRecordEnrollmentOwnershipChecker enrollmentOwnership;
    private final AcademicRecordGradeItemFinder gradeItemFinder;

    public ListMyCourseEnrollmentGradeItemsService(
            AcademicRecordEnrollmentOwnershipChecker enrollmentOwnership, AcademicRecordGradeItemFinder gradeItemFinder) {
        this.enrollmentOwnership = enrollmentOwnership;
        this.gradeItemFinder = gradeItemFinder;
    }

    @Override
    public List<StudentGradeItemResponse> list(Long studentId, Long enrollmentId) {
        enrollmentOwnership.requireCourseEnrollmentOf(enrollmentId, studentId);
        return gradeItemFinder.listCourseEnrollmentGradeItems(enrollmentId);
    }
}
