package com.red_escuela.backend.attendance.api;

import com.red_escuela.backend.shared.domain.DocumentType;
import java.time.LocalDate;

public record HistoricalAttendance(DocumentType documentType, String documentNumber, Long sectionId, LocalDate classDate,
        String status) {
}
