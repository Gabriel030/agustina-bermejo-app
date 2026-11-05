package com.red_escuela.backend.enrollment.api;

import java.time.LocalDate;

public record ActiveCourseSectionView(Long sectionEnrollmentId, LocalDate termEndDate) {
}
