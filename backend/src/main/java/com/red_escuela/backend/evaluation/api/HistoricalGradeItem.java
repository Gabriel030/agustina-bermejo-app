package com.red_escuela.backend.evaluation.api;

import com.red_escuela.backend.shared.domain.DocumentType;
import java.math.BigDecimal;
import java.time.LocalDate;

public record HistoricalGradeItem(DocumentType documentType, String documentNumber, Long sectionId,
        String itemType, int sequenceNumber, LocalDate date, BigDecimal score) {
}
