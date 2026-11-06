package com.red_escuela.backend.billing.run.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record DiscardDraftInvoicesRequest(@NotEmpty List<@NotNull Long> invoiceIds, @NotBlank @Size(max = 500) String reason) {
}
