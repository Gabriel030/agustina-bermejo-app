package com.red_escuela.backend.billing.payment.usecase;

import com.red_escuela.backend.billing.payment.domain.RefundEligibility;
import com.red_escuela.backend.billing.payment.domain.RefundPlanOutcome;
import com.red_escuela.backend.billing.payment.dto.RequestRefundRequest;
import com.red_escuela.backend.shared.domain.Currency;

public interface PreviewRefundUseCase {
    RefundPlanOutcome previewOwnRefund(Long currentStudentId, RequestRefundRequest request);

    RefundPlanOutcome previewRefundOnBehalfOfStudent(Long studentId, RequestRefundRequest request);

    RefundEligibility describeOwnRefundEligibility(Long currentStudentId, Currency currency);

    RefundEligibility describeRefundEligibilityOfStudent(Long studentId, Currency currency);
}
