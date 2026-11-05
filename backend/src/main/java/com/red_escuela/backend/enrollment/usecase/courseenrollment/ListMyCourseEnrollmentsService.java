package com.red_escuela.backend.enrollment.usecase.courseenrollment;

import com.red_escuela.backend.enrollment.dto.MyCourseEnrollmentResponse;
import com.red_escuela.backend.enrollment.service.CourseEnrollmentService;
import com.red_escuela.backend.shared.UseCase;

import java.util.List;

@UseCase
public class ListMyCourseEnrollmentsService implements ListMyCourseEnrollmentsUseCase {

    private final CourseEnrollmentService courseEnrollmentService;

    public ListMyCourseEnrollmentsService(CourseEnrollmentService courseEnrollmentService) {
        this.courseEnrollmentService = courseEnrollmentService;
    }

    @Override
    public List<MyCourseEnrollmentResponse> list(Long studentId) {
        return courseEnrollmentService.listByStudent(studentId).stream()
                .map(enrollment -> MyCourseEnrollmentResponse.from(
                        enrollment, courseEnrollmentService.calculateApprovedModulesPercentage(enrollment)))
                .toList();
    }
}
