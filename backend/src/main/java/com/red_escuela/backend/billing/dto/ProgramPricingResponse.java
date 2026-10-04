package com.red_escuela.backend.billing.dto;

import com.red_escuela.backend.shared.domain.Money;

public record ProgramPricingResponse(Long programId, Money matriculaCohorte1, Money matriculaCohorte2, Money cuotaMensual) {}
