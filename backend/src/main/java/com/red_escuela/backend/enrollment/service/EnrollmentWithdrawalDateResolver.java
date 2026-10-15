package com.red_escuela.backend.enrollment.service;

import com.red_escuela.backend.enrollment.exception.WithdrawalDateOutOfAllowedRangeException;
import com.red_escuela.backend.enrollment.policy.WithdrawalDateContext;
import com.red_escuela.backend.enrollment.policy.WithdrawalDateWithinAllowedRangePolicy;
import com.red_escuela.backend.shared.domain.policy.PolicySet;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class EnrollmentWithdrawalDateResolver {

    private static final PolicySet<WithdrawalDateContext> WITHDRAWAL_DATE_RULES =
            PolicySet.named("ENROLLMENT_WITHDRAWAL_DATE", List.of(new WithdrawalDateWithinAllowedRangePolicy()));

    private final Clock clock;

    public EnrollmentWithdrawalDateResolver(Clock clock) {
        this.clock = clock;
    }

    public LocalDate resolveEffectiveDate(EnrollmentWithdrawal withdrawal, LocalDateTime enrollmentDate) {
        LocalDate today = LocalDate.now(clock);
        LocalDate effectiveDate = withdrawal.informedEffectiveDate().orElse(today);
        WithdrawalDateContext context = new WithdrawalDateContext(effectiveDate, today,
                Optional.ofNullable(enrollmentDate).map(LocalDateTime::toLocalDate));
        WITHDRAWAL_DATE_RULES.evaluateAllRules(context).throwIfDenied(WithdrawalDateOutOfAllowedRangeException::new);
        return effectiveDate;
    }

    public LocalDateTime completionMomentOf(LocalDate effectiveDate) {
        return effectiveDate.atTime(LocalDateTime.now(clock).toLocalTime());
    }
}
