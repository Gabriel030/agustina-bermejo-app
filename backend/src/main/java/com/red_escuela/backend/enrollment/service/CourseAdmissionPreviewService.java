package com.red_escuela.backend.enrollment.service;

import com.red_escuela.backend.academicstructure.api.CourseCatalogFinder;
import com.red_escuela.backend.enrollment.api.CourseAdmissionChargePreview;
import com.red_escuela.backend.enrollment.api.CourseAdmissionChargePreviewPort;
import com.red_escuela.backend.enrollment.dto.CourseAdmissionPreviewResponse;
import com.red_escuela.backend.enrollment.exception.CourseAdmissionAlreadyChargedException;
import com.red_escuela.backend.enrollment.exception.CourseAdmissionChargeRejectedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CourseAdmissionPreviewService {

    private final CourseCatalogFinder courseCatalogFinder;
    private final CourseAdmissionChargePreviewPort chargePreviewer;

    public CourseAdmissionPreviewService(CourseCatalogFinder courseCatalogFinder, CourseAdmissionChargePreviewPort chargePreviewer) {
        this.courseCatalogFinder = courseCatalogFinder;
        this.chargePreviewer = chargePreviewer;
    }

    @Transactional(readOnly = true)
    public CourseAdmissionPreviewResponse previewAdmissionCharge(Long studentId, Long courseId) {
        Long existingCourseId = courseCatalogFinder.findCourseEntity(courseId).getId();
        CourseAdmissionChargePreview preview = chargePreviewer.previewCourseAdmissionCharge(studentId, existingCourseId);
        return switch (preview.outcome()) {
            case CHARGEABLE -> new CourseAdmissionPreviewResponse(existingCourseId, !preview.amount().isZero(), preview.amount());
            case ALREADY_CHARGED -> throw new CourseAdmissionAlreadyChargedException(studentId, existingCourseId);
            case REJECTED -> throw new CourseAdmissionChargeRejectedException(preview.denials());
        };
    }
}
