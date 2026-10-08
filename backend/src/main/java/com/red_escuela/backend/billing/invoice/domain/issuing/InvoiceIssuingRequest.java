package com.red_escuela.backend.billing.invoice.domain.issuing;

import com.red_escuela.backend.billing.invoice.domain.ChargeKey;
import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.billing.invoice.domain.InvoiceIssuingReason;
import com.red_escuela.backend.billing.invoice.domain.InvoiceType;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import com.red_escuela.backend.shared.domain.statemachine.StatusChangeActor;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Objects;

public record InvoiceIssuingRequest(
        Long studentId,
        Long programEnrollmentId,
        Long courseEnrollmentId,
        Long programId,
        Long courseId,
        Long cohortId,
        FeeType feeType,
        YearMonth period,
        LocalDate dueDate,
        LocalDate pricingDate,
        InvoiceIssuingReason issuingReason,
        Long paymentPlanSelectionId,
        Long templateId,
        Integer enrollmentFeeSlot,
        boolean entryEnrollmentFee,
        NonNegativeMoney explicitAmount,
        String description,
        Long subjectId,
        Long examBoardRegistrationId,
        String idempotencyKey,
        StatusChangeActor actor) {

    public InvoiceIssuingRequest {
        Objects.requireNonNull(studentId, "studentId");
        Objects.requireNonNull(feeType, "feeType");
        Objects.requireNonNull(period, "period");
        Objects.requireNonNull(dueDate, "dueDate");
        Objects.requireNonNull(pricingDate, "pricingDate");
        Objects.requireNonNull(issuingReason, "issuingReason");
        Objects.requireNonNull(actor, "actor");
        if ((programId == null) == (courseId == null)) {
            throw new IllegalArgumentException("A charge belongs to exactly one offering: a program or a course");
        }
        if (programEnrollmentId != null && courseEnrollmentId != null) {
            throw new IllegalArgumentException("A charge belongs to at most one enrollment");
        }
        if (programEnrollmentId != null && programId == null || courseEnrollmentId != null && courseId == null) {
            throw new IllegalArgumentException("The enrollment must match the offering of the charge");
        }
        if (issuingReason == InvoiceIssuingReason.AD_HOC_CHARGE && (explicitAmount == null || idempotencyKey == null || idempotencyKey.isBlank())) {
            throw new IllegalArgumentException("An ad hoc charge needs an explicit amount and an idempotency key");
        }
    }

    public InvoiceType invoiceType() {
        return switch (feeType) {
            case MONTHLY_INSTALLMENT -> InvoiceType.MONTHLY_INSTALLMENT;
            case ENROLLMENT_FEE -> InvoiceType.ENROLLMENT_FEE;
            case EXAM_FEE -> InvoiceType.EXAM_FEE;
            case RETAKEN_SUBJECT_FEE -> InvoiceType.RETAKEN_SUBJECT_FEE;
            case DEGREE_ISSUANCE_FEE -> InvoiceType.DEGREE_ISSUANCE_FEE;
            case ADMINISTRATIVE_FEE -> InvoiceType.ADMINISTRATIVE_FEE;
            case LATE_FEE -> InvoiceType.LATE_FEE;
        };
    }

    public boolean isAdHocCharge() {
        return issuingReason == InvoiceIssuingReason.AD_HOC_CHARGE;
    }

    public boolean belongsToProgram() {
        return programId != null;
    }

    public ChargeKey chargeKey() {
        if (isAdHocCharge()) {
            return ChargeKey.forAdHocCharge(idempotencyKey);
        }
        if (feeType == FeeType.ENROLLMENT_FEE) {
            return programEnrollmentId != null
                    ? ChargeKey.forProgramEnrollmentEnrollmentFee(programEnrollmentId, templateId, enrollmentFeeSlot)
                    : ChargeKey.forProgramAdmissionEnrollmentFee(studentId, templateId, enrollmentFeeSlot);
        }
        if (programEnrollmentId != null) {
            return ChargeKey.forProgramEnrollmentMonthlyInstallment(programEnrollmentId, period);
        }
        return courseEnrollmentId != null
                ? ChargeKey.forCourseEnrollmentMonthlyInstallment(courseEnrollmentId, period)
                : ChargeKey.forCourseAdmissionMonthlyInstallment(studentId, courseId, period);
    }
}
