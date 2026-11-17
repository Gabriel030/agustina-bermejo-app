package com.red_escuela.backend.billing.dto;

import jakarta.validation.constraints.Size;

public record RejectPaymentReceiptRequest(@Size(max = 500) String rejectionReason) {
}
