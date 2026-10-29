package com.red_escuela.backend.billing.annualupfront.service;

import com.red_escuela.backend.billing.annualupfront.domain.AnnualUpfrontQuoteLine;
import com.red_escuela.backend.shared.domain.Money;

import java.time.LocalDateTime;
import java.util.List;

public record AnnualUpfrontRequestView(Long id, String status, Long programEnrollmentId, Long invoiceId, Integer lectiveYear, LocalDateTime paymentDeadline,
                                       List<AnnualUpfrontQuoteLine> lines, Money total, AnnualUpfrontRequestDiscountView discount, LocalDateTime acceptedAt,
                                       String acceptedTermsVersion, boolean paymentUnderReview, String discardReason, LocalDateTime decidedAt) {

    public AnnualUpfrontRequestView {
        lines = List.copyOf(lines);
    }
}
