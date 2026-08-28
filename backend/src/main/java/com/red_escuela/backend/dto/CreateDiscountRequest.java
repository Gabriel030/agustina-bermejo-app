package com.red_escuela.backend.dto;

import com.red_escuela.backend.entity.DiscountType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

// valor no se valida @NotNull acá porque BECA_TOTAL no lo necesita (validado en
// el service según el tipo).
public record CreateDiscountRequest(
        @NotNull DiscountType tipo, BigDecimal valor,
        @NotNull LocalDate vigenteDesde, LocalDate vigenteHasta,
        @NotBlank String motivo) {
}
