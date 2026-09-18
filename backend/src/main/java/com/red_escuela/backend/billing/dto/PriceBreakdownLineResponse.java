package com.red_escuela.backend.billing.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PriceBreakdownLineResponse(String type, BigDecimal amount, String humanLabel, String source, LocalDateTime appliedAt) {
}
