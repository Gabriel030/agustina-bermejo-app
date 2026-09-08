package com.red_escuela.backend.billing.dto;

import jakarta.validation.constraints.NotBlank;

public record RejectPaymentReceiptRequest(@NotBlank String rejectionReason) {
}
