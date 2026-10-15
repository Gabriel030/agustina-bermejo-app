package com.red_escuela.backend.billing.dto;

import com.red_escuela.backend.shared.domain.Money;
import java.util.List;

public record PriceBreakdownResponse(
        Long invoiceId, String feeType, String period, Money baseAmount, String priceRevisionDescription,
        List<PriceBreakdownLineResponse> adjustments,
        Money amountDue, List<PriceBreakdownPaymentResponse> paymentsApplied, Money remainingBalance, String status) {
}
