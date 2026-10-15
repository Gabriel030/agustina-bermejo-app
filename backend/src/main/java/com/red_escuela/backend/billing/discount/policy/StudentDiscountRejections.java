package com.red_escuela.backend.billing.discount.policy;

import com.red_escuela.backend.billing.discount.exception.CurrencyMismatchException;
import com.red_escuela.backend.billing.discount.exception.DiscountDefinitionClosedException;
import com.red_escuela.backend.billing.discount.exception.DiscountInvoiceTypeMismatchException;
import com.red_escuela.backend.billing.discount.exception.InvalidScholarshipPeriodException;
import com.red_escuela.backend.billing.discount.exception.InvalidStudentDiscountException;
import com.red_escuela.backend.billing.discount.exception.RevocationReasonRequiredException;
import com.red_escuela.backend.billing.discount.exception.ScholarshipNotAvailableWithAnnualUpfrontException;
import com.red_escuela.backend.billing.discount.exception.ScholarshipStartBeforeNextInstallmentException;
import com.red_escuela.backend.billing.discount.exception.StudentDiscountProposalExpiredException;
import com.red_escuela.backend.shared.domain.policy.PolicyViolation;

import java.util.List;

public final class StudentDiscountRejections {

    private StudentDiscountRejections() {
    }

    public static RuntimeException rejectionOf(List<PolicyViolation> violations) {
        return switch (violations.get(0).code()) {
            case "DISCOUNT_INVOICE_TYPE_MISMATCH" -> new DiscountInvoiceTypeMismatchException(violations);
            case "SCHOLARSHIP_START_BEFORE_NEXT_INSTALLMENT" -> new ScholarshipStartBeforeNextInstallmentException(violations);
            case "SCHOLARSHIP_NOT_AVAILABLE_WITH_ANNUAL_UPFRONT" -> new ScholarshipNotAvailableWithAnnualUpfrontException(violations);
            case "INVALID_SCHOLARSHIP_PERIOD" -> new InvalidScholarshipPeriodException(violations);
            case "REVOCATION_REASON_REQUIRED" -> new RevocationReasonRequiredException(violations);
            case "STUDENT_DISCOUNT_PROPOSAL_EXPIRED" -> new StudentDiscountProposalExpiredException(violations);
            case "DISCOUNT_DEFINITION_CLOSED" -> new DiscountDefinitionClosedException(violations);
            case "CURRENCY_MISMATCH" -> new CurrencyMismatchException(violations);
            default -> new InvalidStudentDiscountException(violations);
        };
    }
}
