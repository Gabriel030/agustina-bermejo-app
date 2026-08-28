package com.red_escuela.backend.dto;

import com.red_escuela.backend.entity.DiscountType;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

// Exactamente uno de programId/courseId, y tipo restringido a PORCENTAJE/MONTO_FIJO
// (nunca BECA_TOTAL) — validado en el service.
public record CreatePromotionRequest(
        Long programId, Long courseId,
        @NotNull DiscountType tipo, @NotNull BigDecimal valor,
        @NotNull LocalDate vigenteDesde, LocalDate vigenteHasta) {
}
