package com.red_escuela.backend.academicrecord.specialist;

import com.red_escuela.backend.enrollment.api.CourseEnrollmentDetailView;
import com.red_escuela.backend.enrollment.api.CourseEnrollmentFinder;
import org.springframework.stereotype.Service;

@Service
public class AcademicRecordCourseEnrollmentFinder {

    private final CourseEnrollmentFinder courseEnrollmentFinder;

    public AcademicRecordCourseEnrollmentFinder(CourseEnrollmentFinder courseEnrollmentFinder) {
        this.courseEnrollmentFinder = courseEnrollmentFinder;
    }

    public CourseEnrollmentDetailView readOfStudent(Long id, Long studentId) {
        return courseEnrollmentFinder.readDetailOfStudent(id, studentId);
    }
}
