package com.red_escuela.backend.billing.dto;

import com.red_escuela.backend.shared.domain.Money;

public record CoursePricingResponse(Long courseId, Money cuotaMensual) {}
