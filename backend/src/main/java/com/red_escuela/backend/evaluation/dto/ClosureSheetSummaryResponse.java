package com.red_escuela.backend.evaluation.dto;

public record ClosureSheetSummaryResponse(int promote, int goToFinal, int failed, int free, int pending, int conditional) {
}
