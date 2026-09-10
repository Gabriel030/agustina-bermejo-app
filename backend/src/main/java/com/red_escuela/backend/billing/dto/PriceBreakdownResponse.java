package com.red_escuela.backend.billing.dto;

import java.math.BigDecimal;
import java.util.List;

public record PriceBreakdownResponse(
        Long invoiceId, String feeType, String period, BigDecimal baseAmount, String priceRevisionDescription,
        List<PriceBreakdownLineResponse> adjustments,
        BigDecimal amountDue, List<PriceBreakdownPaymentResponse> paymentsApplied, BigDecimal remainingBalance, String status) {
}
