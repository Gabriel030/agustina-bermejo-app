package com.red_escuela.backend.enrollment.policy;

import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

import java.time.LocalDate;
import java.time.YearMonth;

@PolicyRule(violationCodes = {"WITHDRAWAL_DATE_OUT_OF_ALLOWED_RANGE"}, decisions = {"D-249"})
public final class WithdrawalDateWithinAllowedRangePolicy implements Policy<WithdrawalDateContext> {

    @Override
    public String ruleName() {
        return "WithdrawalDateWithinAllowedRangePolicy";
    }

    @Override
    public PolicyVerdict evaluateRule(WithdrawalDateContext context) {
        LocalDate effectiveDate = context.effectiveDate();
        LocalDate earliestAllowed = YearMonth.from(context.today()).minusMonths(1).atDay(1);
        boolean notBeforeEarliest = !effectiveDate.isBefore(earliestAllowed);
        boolean notInTheFuture = !effectiveDate.isAfter(context.today());
        boolean notBeforeEnrollment = context.enrollmentDate().map(enrolledOn -> !effectiveDate.isBefore(enrolledOn)).orElse(true);
        if (notBeforeEarliest && notInTheFuture && notBeforeEnrollment) {
            return PolicyVerdict.allow(ruleName());
        }
        return PolicyVerdict.deny(ruleName(), "WITHDRAWAL_DATE_OUT_OF_ALLOWED_RANGE",
                "La fecha de baja debe estar entre el primer día del mes anterior y hoy, y no ser anterior a la inscripción");
    }
}
