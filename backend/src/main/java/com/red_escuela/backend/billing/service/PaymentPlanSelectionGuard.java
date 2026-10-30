package com.red_escuela.backend.billing.service;

import java.time.Clock;
import com.red_escuela.backend.billing.invoice.exception.AnnualUpfrontOfProgramMustUseRequestEndpointException;
import com.red_escuela.backend.billing.invoice.policy.PaymentPlanChoice;
import com.red_escuela.backend.shared.domain.policy.PolicySet;
import com.red_escuela.backend.billing.entity.PaymentPlanCadence;
import com.red_escuela.backend.billing.entity.PaymentPlanOption;
import com.red_escuela.backend.billing.exception.InvalidPaymentPlanScopeException;
import com.red_escuela.backend.billing.exception.UpfrontWindowClosedException;
import com.red_escuela.backend.billing.exception.NotOwnEnrollmentException;
import com.red_escuela.backend.identity.service.CurrentStudentService;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
public class PaymentPlanSelectionGuard {
    private final CurrentStudentService currentStudentService;
    private final Clock clock;
    private final PolicySet<PaymentPlanChoice> annualUpfrontPolicies;

    public PaymentPlanSelectionGuard(CurrentStudentService currentStudentService, Clock clock, PolicySet<PaymentPlanChoice> paymentPlanSelectionPolicySet) {
        this.currentStudentService = currentStudentService;
        this.clock = clock;
        this.annualUpfrontPolicies = paymentPlanSelectionPolicySet;
    }

    public void validateOwnershipIfStudent(Long ownerStudentId, Long enrollmentId) {
        currentStudentService.findCurrentStudent().ifPresent(student -> {
            if (!student.getId().equals(ownerStudentId)) {
                throw new NotOwnEnrollmentException(student.getId(), enrollmentId);
            }
        });
    }

    public void requireAnnualUpfrontAvailableForProgram(PaymentPlanOption option) {
        annualUpfrontPolicies.evaluateRulesUntilFirstViolation(new PaymentPlanChoice(option.getCadence() == PaymentPlanCadence.ANNUAL_UPFRONT, true))
                .throwIfDenied(AnnualUpfrontOfProgramMustUseRequestEndpointException::new);
    }

    public void validateUpfrontWindow(PaymentPlanOption option) {
        if (option.getCadence() != PaymentPlanCadence.ANNUAL_UPFRONT) {
            return;
        }
        if (!option.isUpfrontWindowEnabled()) {
            throw new UpfrontWindowClosedException(option.getId());
        }
        LocalDate today = LocalDate.now(clock);
        if (today.isBefore(option.getUpfrontWindowStart()) || today.isAfter(option.getUpfrontWindowEnd())) {
            throw new UpfrontWindowClosedException(option.getId());
        }
    }

    public void validateScope(Integer scopeMonths, Integer maxMonths) {
        if (scopeMonths == null || scopeMonths <= 0) {
            throw new InvalidPaymentPlanScopeException("scopeMonths must be a positive number");
        }
        if (maxMonths != null && scopeMonths > maxMonths) {
            throw new InvalidPaymentPlanScopeException("scopeMonths (" + scopeMonths + ") exceeds what remains of the term (" + maxMonths + ")");
        }
    }
}
