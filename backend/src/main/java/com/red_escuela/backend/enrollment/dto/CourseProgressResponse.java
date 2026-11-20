package com.red_escuela.backend.enrollment.dto;

import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;

import java.util.List;

public record CourseProgressResponse(
        Long courseEnrollmentId, List<String> modulosPendientes, boolean elegibleParaCompletar,
        List<ModuleEnrollmentView> moduleEnrollments) {

    public record ModuleEnrollmentView(Long sectionEnrollmentId, Long moduleId, String moduleName, String sectionName, EnrollmentStatus status) {
    }
}
