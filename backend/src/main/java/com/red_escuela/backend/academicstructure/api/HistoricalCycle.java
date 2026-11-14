package com.red_escuela.backend.academicstructure.api;

import com.red_escuela.backend.academicstructure.domain.TermType;
import java.time.LocalDate;

public record HistoricalCycle(String programName, int year, TermType termType, String description, LocalDate startDate,
        LocalDate endDate) {
}
