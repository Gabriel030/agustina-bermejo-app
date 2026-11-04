package com.red_escuela.backend.enrollment.usecase.courseenrollment;

import com.red_escuela.backend.enrollment.dto.CourseAdmissionPreviewResponse;

public interface PreviewCourseAdmissionUseCase {
    CourseAdmissionPreviewResponse preview(Long studentId, Long courseId);
}
