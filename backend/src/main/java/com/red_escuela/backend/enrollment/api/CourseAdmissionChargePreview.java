package com.red_escuela.backend.enrollment.api;

import com.red_escuela.backend.shared.domain.Money;

public record CourseAdmissionChargePreview(Outcome outcome, Money amount, Long existingInvoiceId, String denials) {

    public enum Outcome {
        CHARGEABLE, ALREADY_CHARGED, REJECTED
    }

    public static CourseAdmissionChargePreview chargeable(Money amount) {
        return new CourseAdmissionChargePreview(Outcome.CHARGEABLE, amount, null, null);
    }

    public static CourseAdmissionChargePreview alreadyCharged(Long invoiceId) {
        return new CourseAdmissionChargePreview(Outcome.ALREADY_CHARGED, null, invoiceId, null);
    }

    public static CourseAdmissionChargePreview rejected(String denials) {
        return new CourseAdmissionChargePreview(Outcome.REJECTED, null, null, denials);
    }
}
