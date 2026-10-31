package com.red_escuela.backend.academicrecord.usecase.studentrecord;

import com.red_escuela.backend.academicrecord.dto.StudentGradeItemResponse;
import com.red_escuela.backend.academicrecord.service.AcademicRecordEnrollmentOwnership;
import com.red_escuela.backend.academicrecord.service.AcademicRecordGradeItemsFinder;
import com.red_escuela.backend.shared.UseCase;

import java.util.List;

@UseCase
public class ListMyEnrollmentGradeItemsService implements ListMyEnrollmentGradeItemsUseCase {

    private final AcademicRecordEnrollmentOwnership enrollmentOwnership;
    private final AcademicRecordGradeItemsFinder gradeItemsFinder;

    public ListMyEnrollmentGradeItemsService(
            AcademicRecordEnrollmentOwnership enrollmentOwnership, AcademicRecordGradeItemsFinder gradeItemsFinder) {
        this.enrollmentOwnership = enrollmentOwnership;
        this.gradeItemsFinder = gradeItemsFinder;
    }

    @Override
    public List<StudentGradeItemResponse> list(Long studentId, Long enrollmentId) {
        enrollmentOwnership.requireProgramEnrollmentOf(enrollmentId, studentId);
        return gradeItemsFinder.findByProgramEnrollment(enrollmentId);
    }
}
