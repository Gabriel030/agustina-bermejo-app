package com.red_escuela.backend.billing.payment.domain;

import com.red_escuela.backend.shared.domain.Money;

public record PlannedCheckoutItem(Long invoiceId, int position, Money outstandingAtSelection, boolean coveredByDeclaredAmount, boolean protectedFromOverdue) {
}
