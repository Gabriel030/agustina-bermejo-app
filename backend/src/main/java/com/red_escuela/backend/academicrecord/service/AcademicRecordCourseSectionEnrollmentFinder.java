package com.red_escuela.backend.academicrecord.service;

import com.red_escuela.backend.academicrecord.dto.StudentCourseSectionEnrollmentResponse;
import com.red_escuela.backend.enrollment.api.CourseSectionEnrollmentReader;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AcademicRecordCourseSectionEnrollmentFinder {

    private final CourseSectionEnrollmentReader courseSectionEnrollmentReader;

    public AcademicRecordCourseSectionEnrollmentFinder(CourseSectionEnrollmentReader courseSectionEnrollmentReader) {
        this.courseSectionEnrollmentReader = courseSectionEnrollmentReader;
    }

    public List<StudentCourseSectionEnrollmentResponse> listOfStudent(Long studentId, Long courseEnrollmentId) {
        return courseSectionEnrollmentReader.listOfStudent(studentId, courseEnrollmentId).stream()
                .map(StudentCourseSectionEnrollmentResponse::from)
                .toList();
    }

    public StudentCourseSectionEnrollmentResponse readOfStudent(Long id, Long studentId) {
        return StudentCourseSectionEnrollmentResponse.from(courseSectionEnrollmentReader.readOfStudent(id, studentId));
    }
}
