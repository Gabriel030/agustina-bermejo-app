package com.red_escuela.backend.billing.payment.dto;

import com.red_escuela.backend.billing.payment.domain.RefundPart;
import com.red_escuela.backend.billing.payment.domain.RefundPlanOutcome;
import com.red_escuela.backend.shared.domain.Money;

import java.util.List;

public record RefundPreviewResponse(boolean allowed, RefundBlockResponse blockedBy, List<RefundPreviewPartResponse> parts) {

    public record RefundPreviewPartResponse(Long paymentId, String methodType, Money amount, String destinationAccount, String destinationHolder, boolean destinationComplete) {

        static RefundPreviewPartResponse from(RefundPart part) {
            return new RefundPreviewPartResponse(part.paymentId(), part.methodType().name(), part.amount().value(), part.destination().account(), part.destination().holder(),
                    part.destination().isComplete());
        }
    }

    public static RefundPreviewResponse from(RefundPlanOutcome outcome) {
        RefundBlockResponse blockedBy = outcome.violations().stream().findFirst().map(RefundBlockResponse::from).orElse(null);
        return new RefundPreviewResponse(outcome.isAllowed(), blockedBy, outcome.parts().stream().map(RefundPreviewPartResponse::from).toList());
    }
}
