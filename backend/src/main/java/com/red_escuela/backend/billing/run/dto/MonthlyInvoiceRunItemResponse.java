package com.red_escuela.backend.billing.run.dto;

import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceEnrollmentType;
import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceRunItem;
import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceRunItemResult;

import java.time.LocalDateTime;
import java.util.List;

public record MonthlyInvoiceRunItemResponse(Long id, MonthlyInvoiceEnrollmentType enrollmentType, Long enrollmentId, MonthlyInvoiceRunItemResult result,
                                            String decisionCode, String errorCode, String message, int attempts, LocalDateTime nextRetryAt, List<Long> invoiceIds) {

    public static MonthlyInvoiceRunItemResponse of(MonthlyInvoiceRunItem item) {
        return new MonthlyInvoiceRunItemResponse(item.getId(), item.getEnrollmentType(), item.getEnrollmentId(), item.getResult(), item.getDecisionCode(),
                item.getErrorCode(), item.getMessage(), item.getAttempts(), item.getNextRetryAt(), item.getInvoiceIds());
    }
}
