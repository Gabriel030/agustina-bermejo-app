package com.red_escuela.backend.billing.dto;

import com.red_escuela.backend.shared.domain.Money;
import java.time.LocalDateTime;

public record PriceBreakdownLineResponse(String type, Money amount, String humanLabel, String source, LocalDateTime appliedAt) {
}
