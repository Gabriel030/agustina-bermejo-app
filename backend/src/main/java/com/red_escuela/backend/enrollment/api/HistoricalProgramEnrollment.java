package com.red_escuela.backend.enrollment.api;

import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.shared.domain.DocumentType;
import java.time.LocalDate;

public record HistoricalProgramEnrollment(DocumentType documentType, String documentNumber, String programName,
        LocalDate enrollmentDate, ProgramEnrollmentStatus status, LocalDate completionDate, LocalDate withdrawalDate) {
}
