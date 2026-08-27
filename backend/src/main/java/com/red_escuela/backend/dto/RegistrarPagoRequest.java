package com.red_escuela.backend.dto;

import com.red_escuela.backend.entity.PaymentMethod;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record RegistrarPagoRequest(@NotNull BigDecimal amount, @NotNull PaymentMethod method, String notes) {
}
