package com.red_escuela.backend.billing.run.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record ApproveDraftInvoicesRequest(@NotEmpty List<@NotNull Long> invoiceIds) {
}
