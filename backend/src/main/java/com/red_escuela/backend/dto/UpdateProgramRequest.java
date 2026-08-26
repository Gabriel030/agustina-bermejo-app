package com.red_escuela.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record UpdateProgramRequest(
        String name, Integer totalYears, BigDecimal price,
        Integer totalCreditsRequired, LocalDateTime admissionStart, LocalDateTime admissionEnd) {
}
