package com.red_escuela.backend.billing.api;

public record HistoricalProgramCohort(String programName, int year, HistoricalCohortIntake intake) {
}
