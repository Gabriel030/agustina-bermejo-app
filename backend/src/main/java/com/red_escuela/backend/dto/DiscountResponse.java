package com.red_escuela.backend.dto;

import com.red_escuela.backend.entity.Discount;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DiscountResponse(
        Long id, Long studentId, String tipo, BigDecimal valor,
        LocalDate vigenteDesde, LocalDate vigenteHasta, String motivo) {

    public static DiscountResponse from(Discount discount) {
        return new DiscountResponse(
                discount.getId(), discount.getStudent().getId(), discount.getTipo().name(), discount.getValor(),
                discount.getVigenteDesde(), discount.getVigenteHasta(), discount.getMotivo());
    }
}
