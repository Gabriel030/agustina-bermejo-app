package com.red_escuela.backend.billing.run.operation;

import com.red_escuela.backend.billing.collections.operation.OverdueDetectionResult;
import com.red_escuela.backend.billing.collections.operation.OverdueInvoiceDetectionStep;
import com.red_escuela.backend.billing.run.domain.BillingRunStep;
import com.red_escuela.backend.billing.run.domain.BillingRunStepOutcome;
import com.red_escuela.backend.billing.run.domain.BillingRunSubjectType;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
public class OverdueDetectionBillingRunStep extends AbstractBillingRunStep {
    private final OverdueInvoiceDetectionStep detectionStep;

    public OverdueDetectionBillingRunStep(OverdueInvoiceDetectionStep detectionStep) {
        this.detectionStep = detectionStep;
    }

    @Override
    public BillingRunStep step() {
        return BillingRunStep.OVERDUE_DETECTION;
    }

    @Override
    protected BillingRunStepOutcome executeStepForBusinessDate(LocalDate businessDate) {
        OverdueDetectionResult result = detectionStep.detectOverdueInvoices(businessDate);
        return BillingRunStepOutcome.ofCandidates(result.candidates(), translateReportedErrors(result.errors(), "invoiceId", BillingRunSubjectType.INVOICE));
    }
}
