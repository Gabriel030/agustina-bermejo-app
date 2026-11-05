package com.red_escuela.backend.academicrecord.service;

import com.red_escuela.backend.academicrecord.dto.StudentGradeItemResponse;
import com.red_escuela.backend.evaluation.api.GradeItemFinder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AcademicRecordGradeItemFinder {

    private final GradeItemFinder gradeItemFinder;

    public AcademicRecordGradeItemFinder(GradeItemFinder gradeItemFinder) {
        this.gradeItemFinder = gradeItemFinder;
    }

    public List<StudentGradeItemResponse> listProgramEnrollmentGradeItems(Long enrollmentId) {
        return gradeItemFinder.findByProgramEnrollment(enrollmentId).stream()
                .map(StudentGradeItemResponse::from)
                .toList();
    }

    public List<StudentGradeItemResponse> listCourseEnrollmentGradeItems(Long enrollmentId) {
        return gradeItemFinder.findByCourseEnrollment(enrollmentId).stream()
                .map(StudentGradeItemResponse::from)
                .toList();
    }
}
