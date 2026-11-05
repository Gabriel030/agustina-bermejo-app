package com.red_escuela.backend.billing.payment.dto;

import com.red_escuela.backend.shared.domain.Money;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record RegisterChargebackRequest(@NotNull Money amount, @NotNull LocalDate chargedBackOn, String reference) {
}
