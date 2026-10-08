package com.red_escuela.backend.billing.invoice.domain.issuing;

import com.red_escuela.backend.billing.invoice.domain.ChargeKey;
import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.billing.invoice.domain.InvoiceIssuingReason;
import com.red_escuela.backend.billing.invoice.domain.InvoiceType;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import com.red_escuela.backend.shared.domain.statemachine.StatusChangeActor;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

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

    public boolean hasEnrollment() {
        return programEnrollmentId != null || courseEnrollmentId != null;
    }

    public boolean isAdHocCharge() {
        return issuingReason == InvoiceIssuingReason.AD_HOC_CHARGE;
    }

    public boolean belongsToProgram() {
        return programId != null;
    }

    public Optional<ChargeKey> findChargeKey() {
        if (isAdHocCharge()) {
            return Optional.of(ChargeKey.forAdHocCharge(studentId, feeType, idempotencyKey));
        }
        if (feeType == FeeType.ENROLLMENT_FEE) {
            return findEnrollmentFeeChargeKey();
        }
        if (programEnrollmentId != null) {
            return Optional.of(ChargeKey.forProgramEnrollmentMonthlyInstallment(programEnrollmentId, period));
        }
        if (courseEnrollmentId != null) {
            return Optional.of(ChargeKey.forCourseEnrollmentMonthlyInstallment(courseEnrollmentId, period));
        }
        return belongsToProgram() ? Optional.empty() : Optional.of(ChargeKey.forCourseAdmissionMonthlyInstallment(studentId, courseId, period));
    }

    public ChargeKey chargeKey() {
        return findChargeKey().orElseThrow(() -> new IllegalStateException("The charge has no key yet: template and enrollment fee slot are required"));
    }

    public List<ChargeKey> findEquivalentAdmissionChargeKeys() {
        if (isAdHocCharge() || programEnrollmentId == null && courseEnrollmentId == null) {
            return List.of();
        }
        if (feeType == FeeType.ENROLLMENT_FEE) {
            return templateId == null || enrollmentFeeSlot == null ? List.of() : List.of(ChargeKey.forProgramAdmissionEnrollmentFee(studentId, templateId, enrollmentFeeSlot));
        }
        return courseEnrollmentId == null ? List.of() : List.of(ChargeKey.forCourseAdmissionMonthlyInstallment(studentId, courseId, period));
    }

    private Optional<ChargeKey> findEnrollmentFeeChargeKey() {
        if (!belongsToProgram() || templateId == null || enrollmentFeeSlot == null) {
            return Optional.empty();
        }
        return Optional.of(programEnrollmentId != null
                ? ChargeKey.forProgramEnrollmentEnrollmentFee(programEnrollmentId, templateId, enrollmentFeeSlot)
                : ChargeKey.forProgramAdmissionEnrollmentFee(studentId, templateId, enrollmentFeeSlot));
    }
}
