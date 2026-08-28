package com.red_escuela.backend.dto;

import com.red_escuela.backend.entity.FeeScheduleConcept;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

// Exactamente uno de programId/courseId, validado en el service — mismo criterio
// que CreateFeeScheduleRequest.
public record CreateInvoiceFromFeeScheduleRequest(
        Long programId, Long courseId,
        @NotNull FeeScheduleConcept concept,
        @NotNull LocalDate dueDate) {
}
