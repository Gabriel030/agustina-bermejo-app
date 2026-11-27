package com.red_escuela.backend.enrollment.usecase.courseenrollment;

import com.red_escuela.backend.enrollment.dto.CreateCourseEnrollmentRequest;
import com.red_escuela.backend.enrollment.dto.CourseEnrollmentResponse;
import com.red_escuela.backend.enrollment.service.CourseEnrollmentApplicationService;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class EnrollCourseByStaff {

    private final CourseEnrollmentApplicationService courseEnrollmentApplicationService;

    public EnrollCourseByStaff(CourseEnrollmentApplicationService courseEnrollmentApplicationService) {
        this.courseEnrollmentApplicationService = courseEnrollmentApplicationService;
    }

    public CourseEnrollmentResponse enroll(Long studentId, CreateCourseEnrollmentRequest request) {
        return CourseEnrollmentResponse.from(courseEnrollmentApplicationService.enrollByStaff(studentId, request));
    }
}
