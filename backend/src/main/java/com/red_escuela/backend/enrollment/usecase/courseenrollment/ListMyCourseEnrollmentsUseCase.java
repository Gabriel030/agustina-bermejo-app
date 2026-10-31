package com.red_escuela.backend.enrollment.usecase.courseenrollment;

import com.red_escuela.backend.enrollment.dto.MyCourseEnrollmentResponse;

import java.util.List;

public interface ListMyCourseEnrollmentsUseCase {
    List<MyCourseEnrollmentResponse> list(Long studentId);
}
