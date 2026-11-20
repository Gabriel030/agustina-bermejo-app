package com.red_escuela.backend.billing.operation;

import com.red_escuela.backend.shared.domain.Money;

import java.util.UUID;

public record ReportedGatewayAllocation(UUID publicReference, Money amountApplied) {
}
