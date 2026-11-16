package com.red_escuela.backend.graduation.api;

import com.red_escuela.backend.shared.domain.DocumentType;
import java.time.LocalDate;

public record HistoricalGraduation(DocumentType documentType, String documentNumber, String programName,
        LocalDate graduationDate, String registryNumber, String legacyTitleNumber) {
}
