package com.red_escuela.backend.billing.note.domain;

import com.red_escuela.backend.shared.domain.NonNegativeMoney;

public record CreditableLineFacts(int lineNumber, NonNegativeMoney creditableAmount, NonNegativeMoney freeCreditedAmount) {
}
