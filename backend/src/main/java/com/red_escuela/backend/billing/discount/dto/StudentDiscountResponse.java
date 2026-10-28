package com.red_escuela.backend.billing.discount.dto;

import com.red_escuela.backend.billing.discount.domain.DiscountDefinitionType;
import com.red_escuela.backend.billing.discount.domain.StudentDiscount;
import com.red_escuela.backend.billing.discount.domain.StudentDiscountEndCause;
import com.red_escuela.backend.billing.discount.domain.StudentDiscountLabels;
import com.red_escuela.backend.billing.discount.domain.StudentDiscountStatus;
import com.red_escuela.backend.billing.discount.domain.StudentDiscountUsage;
import com.red_escuela.backend.billing.discount.domain.StudentDiscountWithUsage;
import com.red_escuela.backend.billing.invoice.domain.DiscountType;
import com.red_escuela.backend.billing.invoice.domain.InvoiceType;
import com.red_escuela.backend.shared.domain.MonetaryAdjustment;
import com.red_escuela.backend.shared.domain.Money;
import org.springframework.lang.Nullable;

import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

public record StudentDiscountResponse(Long id, DiscountType type, InvoiceType applicableInvoiceType, MonetaryAdjustment calculation, @Nullable Money capPerInvoice,
                                      int priority, YearMonth fromPeriod, YearMonth toPeriod, StudentDiscountStatus status, Instant grantedAt, @Nullable Long grantedById,
                                      @Nullable String reason, @Nullable Long authorizationRequestId, @Nullable Instant revokedAt, @Nullable String revocationReason,
                                      @Nullable YearMonth revokedFromPeriod, @Nullable Long programEnrollmentId, @Nullable Long courseEnrollmentId,
                                      @Nullable List<AppliedStudentDiscountInstallmentResponse> appliedInstallments, @Nullable List<YearMonth> lostPeriods,
                                      Long studentId, @Nullable Long discountDefinitionId, @Nullable DiscountDefinitionType definitionType, @Nullable String definitionName,
                                      @Nullable String enrollmentName, @Nullable String studentName, @Nullable Integer lostBenefitCount,
                                      @Nullable StudentDiscountEndCause endCause, @Nullable String endCauseText) {

    public static StudentDiscountResponse from(StudentDiscount discount) {
        return from(discount, null, StudentDiscountLabels.none(), false);
    }

    public static StudentDiscountResponse from(StudentDiscountWithUsage discountWithUsage) {
        return from(discountWithUsage.discount(), discountWithUsage.usage(), StudentDiscountLabels.none(), false);
    }

    public static StudentDiscountResponse forStaff(StudentDiscountWithUsage discountWithUsage, StudentDiscountLabels labels) {
        return from(discountWithUsage.discount(), discountWithUsage.usage(), labels, false);
    }

    public static StudentDiscountResponse forStudent(StudentDiscountWithUsage discountWithUsage, StudentDiscountLabels labels) {
        return from(discountWithUsage.discount(), discountWithUsage.usage(), labels, true);
    }

    private static StudentDiscountResponse from(StudentDiscount discount, @Nullable StudentDiscountUsage usage, StudentDiscountLabels labels, boolean hideInternal) {
        Optional<StudentDiscountEndCause> endCause = StudentDiscountEndCause.of(discount.getStatus());
        return new StudentDiscountResponse(discount.getId(), discount.getDiscountType(), discount.getApplicableInvoiceType(), discount.getCalculation(),
                discount.getCapPerInvoice() == null ? null : discount.getCapPerInvoice().value(), discount.getPriority(), discount.getFromPeriod(), discount.getToPeriod(),
                discount.getStatus(), discount.getGrantedAt(), hideInternal ? null : discount.getGrantedById(), hideInternal ? null : discount.getReason(),
                hideInternal ? null : discount.getAuthorizationRequestId(), discount.getRevokedAt(),
                hideInternal ? null : discount.getRevocationReason(), discount.getRevokedFromPeriod(), discount.getProgramEnrollmentId(), discount.getCourseEnrollmentId(),
                usage == null ? null : usage.appliedInstallments().stream().map(AppliedStudentDiscountInstallmentResponse::from).toList(),
                usage == null ? null : usage.lostPeriods(), discount.getStudentId(), discount.getDiscountDefinitionId(), labels.definitionType(), labels.definitionName(),
                labels.enrollmentName(), labels.studentName(), usage == null ? null : usage.lostPeriods().size(), endCause.orElse(null),
                endCause.map(StudentDiscountEndCause::studentFacingText).orElse(null));
    }
}
