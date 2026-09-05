package com.red_escuela.backend.dto;

import com.red_escuela.backend.entity.Promotion;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PromotionResponse(
        Long id, Long programId, Long courseId, String tipo, BigDecimal valor,
        LocalDate vigenteDesde, LocalDate vigenteHasta) {

    public static PromotionResponse from(Promotion promotion) {
        return new PromotionResponse(
                promotion.getId(),
                promotion.getProgram() == null ? null : promotion.getProgram().getId(),
                promotion.getCourse() == null ? null : promotion.getCourse().getId(),
                promotion.getType().name(), promotion.getAmount(),
                promotion.getValidFrom(), promotion.getValidUntil());
    }
}
