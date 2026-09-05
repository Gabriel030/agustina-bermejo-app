package com.red_escuela.backend.dto;

import com.red_escuela.backend.entity.FeeSchedule;

import java.math.BigDecimal;
import java.time.LocalDate;

public record FeeScheduleResponse(
        Long id, Long programId, Long courseId, String concept, BigDecimal amount,
        LocalDate vigenteDesde, Long createdById) {

    public static FeeScheduleResponse from(FeeSchedule feeSchedule) {
        return new FeeScheduleResponse(
                feeSchedule.getId(),
                feeSchedule.getProgram() == null ? null : feeSchedule.getProgram().getId(),
                feeSchedule.getCourse() == null ? null : feeSchedule.getCourse().getId(),
                feeSchedule.getConcept().name(),
                feeSchedule.getAmount(),
                feeSchedule.getValidFrom(),
                feeSchedule.getCreatedBy().getId());
    }
}
