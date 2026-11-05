package com.red_escuela.backend.enrollment.api;

public interface CourseAdmissionChargePreviewPort {

    CourseAdmissionChargePreview previewCourseAdmissionCharge(Long studentId, Long courseId);
}
