package com.red_escuela.backend.billing.payment.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import com.red_escuela.backend.billing.payment.domain.RefundPart;
import com.red_escuela.backend.billing.payment.domain.RefundPlanOutcome;
import com.red_escuela.backend.shared.domain.Money;

import java.util.List;

public record RefundPreviewResponse(boolean allowed, @Schema(nullable = true) RefundBlockResponse blockedBy, List<RefundPreviewPartResponse> parts) {

    public record RefundPreviewPartResponse(Long paymentId, String methodType, Money amount, @Schema(nullable = true) String destinationAccount, @Schema(nullable = true) String destinationHolder, boolean destinationComplete) {

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
