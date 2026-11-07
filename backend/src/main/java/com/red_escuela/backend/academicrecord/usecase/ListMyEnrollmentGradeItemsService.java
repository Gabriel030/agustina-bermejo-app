package com.red_escuela.backend.academicrecord.usecase;

import com.red_escuela.backend.academicrecord.dto.StudentGradeItemResponse;
import com.red_escuela.backend.academicrecord.specialist.AcademicRecordEnrollmentOwnershipChecker;
import com.red_escuela.backend.academicrecord.specialist.AcademicRecordGradeItemFinder;
import com.red_escuela.backend.shared.UseCase;

import java.util.List;

@UseCase
public class ListMyEnrollmentGradeItemsService implements ListMyEnrollmentGradeItemsUseCase {

    private final AcademicRecordEnrollmentOwnershipChecker enrollmentOwnership;
    private final AcademicRecordGradeItemFinder gradeItemFinder;

    public ListMyEnrollmentGradeItemsService(
            AcademicRecordEnrollmentOwnershipChecker enrollmentOwnership, AcademicRecordGradeItemFinder gradeItemFinder) {
        this.enrollmentOwnership = enrollmentOwnership;
        this.gradeItemFinder = gradeItemFinder;
    }

    @Override
    public List<StudentGradeItemResponse> list(Long studentId, Long enrollmentId) {
        enrollmentOwnership.requireProgramEnrollmentOf(enrollmentId, studentId);
        return gradeItemFinder.listProgramEnrollmentGradeItems(enrollmentId);
    }
}
