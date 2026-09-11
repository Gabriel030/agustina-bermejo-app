package com.red_escuela.backend.enrollment.dto;

import java.util.List;

public record CourseProgressResponse(
        Long courseEnrollmentId, List<String> modulosPendientes, boolean elegibleParaCompletar) {
}
