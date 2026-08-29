package com.red_escuela.backend.dto;

import com.red_escuela.backend.entity.Program;

import java.math.BigDecimal;

public record ProgramResponse(
        Long id, String name, Integer totalYears, BigDecimal price, String status,
        Integer totalCreditsRequired, Integer cuotasPorAnioLectivo) {

    public static ProgramResponse from(Program program) {
        return new ProgramResponse(
                program.getId(), program.getName(), program.getTotalYears(), program.getPrice(),
                program.getStatus() == null ? null : program.getStatus().name(),
                program.getTotalCreditsRequired(), program.getCuotasPorAnioLectivo());
    }
}
