package com.red_escuela.backend.billing.api;

import java.time.LocalDateTime;

public record AdmissionReceiptSummary(String status, LocalDateTime uploadedAt) {
}
