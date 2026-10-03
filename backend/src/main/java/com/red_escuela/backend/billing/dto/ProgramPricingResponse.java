package com.red_escuela.backend.billing.dto;

import java.math.BigDecimal;

public record ProgramPricingResponse(Long programId, BigDecimal matriculaCohorte1, BigDecimal matriculaCohorte2, BigDecimal cuotaMensual) {}
