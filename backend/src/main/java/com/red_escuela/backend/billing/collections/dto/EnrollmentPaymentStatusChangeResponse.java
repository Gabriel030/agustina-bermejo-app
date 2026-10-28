package com.red_escuela.backend.billing.collections.dto;

import com.red_escuela.backend.billing.api.EnrollmentPaymentStatusChangeView;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record EnrollmentPaymentStatusChangeResponse(String event, String fromStatus, String toStatus, String reason, Long actorPersonId,
                                                    String actorProcess, LocalDate businessDate, LocalDateTime occurredAt) {

    public static EnrollmentPaymentStatusChangeResponse from(EnrollmentPaymentStatusChangeView view) {
        return new EnrollmentPaymentStatusChangeResponse(view.event(), view.fromStatus() == null ? null : view.fromStatus().name(), view.toStatus().name(),
                view.reason(), view.actorPersonId(), view.actorProcess(), view.businessDate(), view.occurredAt());
    }
}
