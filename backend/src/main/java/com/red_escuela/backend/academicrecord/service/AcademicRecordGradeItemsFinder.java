package com.red_escuela.backend.academicrecord.service;

import com.red_escuela.backend.academicrecord.dto.StudentGradeItemResponse;
import com.red_escuela.backend.evaluation.api.EnrollmentGradeItemsFinder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AcademicRecordGradeItemsFinder {

    private final EnrollmentGradeItemsFinder enrollmentGradeItemsFinder;

    public AcademicRecordGradeItemsFinder(EnrollmentGradeItemsFinder enrollmentGradeItemsFinder) {
        this.enrollmentGradeItemsFinder = enrollmentGradeItemsFinder;
    }

    public List<StudentGradeItemResponse> findByProgramEnrollment(Long enrollmentId) {
        return enrollmentGradeItemsFinder.findByProgramEnrollment(enrollmentId).stream()
                .map(StudentGradeItemResponse::from)
                .toList();
    }
}
