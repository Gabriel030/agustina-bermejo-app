package com.red_escuela.backend.billing.run.dto;

import com.red_escuela.backend.billing.run.domain.BillingRunItem;
import com.red_escuela.backend.billing.run.domain.BillingRunItemStatus;
import com.red_escuela.backend.billing.run.domain.BillingRunSubjectType;

public record BillingRunItemResponse(Long id, BillingRunSubjectType subjectType, Long subjectId, BillingRunItemStatus status, String decisionCode, String errorCode,
                                     String message) {

    public static BillingRunItemResponse of(BillingRunItem item) {
        return new BillingRunItemResponse(item.getId(), item.getSubjectType(), item.getSubjectId(), item.getStatus(), item.getDecisionCode(), item.getErrorCode(),
                item.getMessage());
    }
}
