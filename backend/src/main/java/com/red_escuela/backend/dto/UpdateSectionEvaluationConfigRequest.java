package com.red_escuela.backend.dto;

import java.time.LocalDate;

public record UpdateSectionEvaluationConfigRequest(
        Integer tpCount,
        LocalDate tp1DueDate, LocalDate tp2DueDate, LocalDate tp3DueDate,
        LocalDate exam1Date, LocalDate exam2Date, LocalDate exam3Date, LocalDate exam4Date,
        LocalDate finalCall1Date, LocalDate finalCall2Date) {
}
