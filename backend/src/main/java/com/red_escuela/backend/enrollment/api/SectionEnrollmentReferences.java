package com.red_escuela.backend.enrollment.api;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record SectionEnrollmentReferences(Long studentId, Long programEnrollmentId, LocalDate programEnrollmentDate,
        LocalDateTime sectionTermStart) {
}
