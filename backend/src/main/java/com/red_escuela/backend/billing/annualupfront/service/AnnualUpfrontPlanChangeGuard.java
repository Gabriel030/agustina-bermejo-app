package com.red_escuela.backend.billing.annualupfront.service;

import com.red_escuela.backend.billing.annualupfront.exception.AnnualUpfrontPlanCannotBeChangedException;
import com.red_escuela.backend.billing.annualupfront.policy.AnnualUpfrontPlanChange;
import com.red_escuela.backend.billing.annualupfront.repository.AnnualUpfrontRequestRepository;
import com.red_escuela.backend.shared.domain.policy.PolicySet;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;

@Service
public class AnnualUpfrontPlanChangeGuard {
    private final AnnualUpfrontRequestRepository requestRepository;
    private final PolicySet<AnnualUpfrontPlanChange> planChangePolicies;
    private final Clock clock;

    public AnnualUpfrontPlanChangeGuard(AnnualUpfrontRequestRepository requestRepository, PolicySet<AnnualUpfrontPlanChange> annualUpfrontPlanChangePolicies, Clock clock) {
        this.requestRepository = requestRepository;
        this.planChangePolicies = annualUpfrontPlanChangePolicies;
        this.clock = clock;
    }

    public void requirePlanChangeAllowed(Long programEnrollmentId) {
        boolean confirmed = requestRepository.existsConfirmedOfLectiveYear(programEnrollmentId, LocalDate.now(clock).getYear());
        planChangePolicies.evaluateRulesUntilFirstViolation(new AnnualUpfrontPlanChange(confirmed)).throwIfDenied(AnnualUpfrontPlanCannotBeChangedException::new);
    }
}
