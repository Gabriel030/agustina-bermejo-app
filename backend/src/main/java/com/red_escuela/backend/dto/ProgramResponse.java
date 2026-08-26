package com.red_escuela.backend.dto;

import com.red_escuela.backend.entity.Program;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ProgramResponse(
        Long id, String name, Integer totalYears, BigDecimal price, String status,
        Integer totalCreditsRequired, LocalDateTime admissionStart, LocalDateTime admissionEnd) {

    public static ProgramResponse from(Program program) {
        return new ProgramResponse(
                program.getId(), program.getName(), program.getTotalYears(), program.getPrice(),
                program.getStatus() == null ? null : program.getStatus().name(),
                program.getTotalCreditsRequired(), program.getAdmissionStart(), program.getAdmissionEnd());
    }
}
