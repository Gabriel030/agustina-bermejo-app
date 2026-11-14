package com.red_escuela.backend.evaluation.api;

import java.time.LocalDate;

public record HistoricalClosure(Long sectionId, LocalDate closedOn, String reportNumber, String book, String folio) {
}
