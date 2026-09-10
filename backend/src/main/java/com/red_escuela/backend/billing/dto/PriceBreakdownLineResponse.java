package com.red_escuela.backend.billing.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// humanLabel/source se arman en el service (no en el frontend) — ej. humanLabel "Recargo
// automático", source "Sistema (mora automática)" o "Administrativo: Ana Gómez".
public record PriceBreakdownLineResponse(String type, BigDecimal amount, String humanLabel, String source, LocalDateTime appliedAt) {
}
