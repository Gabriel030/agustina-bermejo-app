package com.red_escuela.backend.dto;

import java.math.BigDecimal;

public record UpdateProgramRequest(
        String name, Integer totalYears, BigDecimal price,
        Integer totalCreditsRequired, Integer cuotasPorAnioLectivo) {
}
