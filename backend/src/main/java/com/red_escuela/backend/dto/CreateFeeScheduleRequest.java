package com.red_escuela.backend.dto;

import com.red_escuela.backend.entity.FeeScheduleConcept;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;

// Exactamente uno de programId/courseId (validado en el service, no acá — el
// XOR es una regla de negocio, no de forma del request).
public record CreateFeeScheduleRequest(
        Long programId, Long courseId,
        @NotNull FeeScheduleConcept concept,
        @NotNull @Positive BigDecimal amount,
        @NotNull LocalDate vigenteDesde) {
}
