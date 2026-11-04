package com.red_escuela.backend.enrollment.usecase.courseenrollment;

import com.red_escuela.backend.enrollment.dto.CourseAdmissionPreviewResponse;
import com.red_escuela.backend.enrollment.service.CourseAdmissionPreviewService;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class PreviewCourseAdmissionService implements PreviewCourseAdmissionUseCase {

    private final CourseAdmissionPreviewService previewService;

    public PreviewCourseAdmissionService(CourseAdmissionPreviewService previewService) {
        this.previewService = previewService;
    }

    @Override
    public CourseAdmissionPreviewResponse preview(Long studentId, Long courseId) {
        return previewService.previewAdmissionCharge(studentId, courseId);
    }
}
