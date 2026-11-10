package com.red_escuela.backend.enrollment.dto;

import java.util.List;

public record ProgramEnrollmentApplicationPage(List<ProgramEnrollmentApplicationResponse> items, long totalElements) {
}
