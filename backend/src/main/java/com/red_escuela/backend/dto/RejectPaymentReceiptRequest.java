package com.red_escuela.backend.dto;

import jakarta.validation.constraints.NotBlank;

public record RejectPaymentReceiptRequest(@NotBlank String reason) {
}
