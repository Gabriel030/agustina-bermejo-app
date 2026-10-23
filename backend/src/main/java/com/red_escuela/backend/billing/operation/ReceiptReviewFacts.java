package com.red_escuela.backend.billing.operation;

import com.red_escuela.backend.shared.domain.Money;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record ReceiptReviewFacts(Money declaredAmount, LocalDate transferDate, String operationNumber, String originAccount, String holderName,
                                 Integer pendingBusinessDays, LocalDateTime reviewAlertSentAt) {
}
