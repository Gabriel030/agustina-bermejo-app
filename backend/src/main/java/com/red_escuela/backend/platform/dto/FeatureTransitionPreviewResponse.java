package com.red_escuela.backend.platform.dto;

import com.red_escuela.backend.shared.domain.BillingShutdownImpact;
import com.red_escuela.backend.platform.domain.FeatureTransitionPreview;
import com.red_escuela.backend.platform.model.BillingMode;
import com.red_escuela.backend.platform.model.FeatureCode;

import java.time.LocalDateTime;
import java.util.List;

public record FeatureTransitionPreviewResponse(
        FeatureCode code,
        BillingMode currentMode,
        BillingMode targetMode,
        long currentVersion,
        Impact impact,
        String confirmationToken,
        LocalDateTime confirmationExpiresAt) {

    public record Impact(
            long openInvoices,
            long overdueInvoices,
            long pendingDraftInvoices,
            long invoicesAwaitingPayment,
            long invoicesUnderPaymentReview,
            long paymentsPendingReconciliation,
            long refundsInProgress,
            long runsInProgress,
            List<String> scheduledJobs) {

        static Impact of(BillingShutdownImpact impact) {
            return new Impact(impact.openInvoices(), impact.overdueInvoices(), impact.pendingDraftInvoices(), impact.invoicesAwaitingPayment(),
                    impact.invoicesUnderPaymentReview(), impact.paymentsPendingReconciliation(), impact.refundsInProgress(), impact.runsInProgress(),
                    impact.scheduledJobs());
        }
    }

    public static FeatureTransitionPreviewResponse of(FeatureTransitionPreview preview) {
        return new FeatureTransitionPreviewResponse(preview.code(), preview.currentMode(), preview.targetMode(), preview.currentVersion(),
                Impact.of(preview.impact()), preview.confirmationToken(), preview.confirmationExpiresAt());
    }
}
