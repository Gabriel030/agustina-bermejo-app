package com.red_escuela.backend.dto;

import java.math.BigDecimal;

public record CoursePricingResponse(Long courseId, BigDecimal cuotaMensual) {
}
