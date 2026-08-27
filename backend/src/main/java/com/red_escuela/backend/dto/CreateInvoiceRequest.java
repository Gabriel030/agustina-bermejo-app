package com.red_escuela.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateInvoiceRequest(@NotBlank String concept, @NotNull BigDecimal amount, @NotNull LocalDate dueDate) {
}
