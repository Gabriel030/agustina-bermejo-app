package com.red_escuela.backend.enrollment.api;

import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.FinalGradeType;
import com.red_escuela.backend.shared.domain.DocumentType;
import java.math.BigDecimal;
import java.time.LocalDate;

public record HistoricalSectionEnrollment(DocumentType documentType, String documentNumber, String programName,
        Long sectionId, int attemptNumber, EnrollmentStatus status, BigDecimal finalScore,
        FinalGradeType finalGradeType, LocalDate finalDate) {
}
