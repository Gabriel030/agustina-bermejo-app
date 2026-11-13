package com.red_escuela.backend.academicstructure.dto;

import com.red_escuela.backend.shared.domain.Money;

public record ProgramListPrices(Money tuition, Money enrollmentFee) {
}
