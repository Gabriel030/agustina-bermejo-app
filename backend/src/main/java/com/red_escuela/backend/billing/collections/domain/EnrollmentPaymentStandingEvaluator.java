package com.red_escuela.backend.billing.collections.domain;

import com.red_escuela.backend.billing.entity.EnrollmentPaymentStatus;

import java.util.ArrayList;
import java.util.List;

public final class EnrollmentPaymentStandingEvaluator {

    private EnrollmentPaymentStandingEvaluator() {
    }

    public static List<EnrollmentPaymentEvent> evaluateStanding(EnrollmentPaymentStatus current, EnrollmentDebtFacts facts, EnrollmentSuspensionRule rule) {
        List<EnrollmentPaymentEvent> events = new ArrayList<>();
        EnrollmentPaymentStatus status = current;
        if (status == EnrollmentPaymentStatus.UP_TO_DATE && facts.hasAnyOverdueInvoice()) {
            events.add(EnrollmentPaymentEvent.OVERDUE_DEBT_DETECTED);
            status = EnrollmentPaymentStatus.OVERDUE;
        }
        if (status == EnrollmentPaymentStatus.OVERDUE) {
            if (!facts.hasAnyOverdueInvoice()) {
                events.add(EnrollmentPaymentEvent.OVERDUE_DEBT_CLEARED);
            } else if (rule.suspends(facts)) {
                events.add(EnrollmentPaymentEvent.MONTH_CLOSED_WITH_UNPAID_INSTALLMENT);
            }
        } else if (status == EnrollmentPaymentStatus.SUSPENDED && !facts.hasAnyOverdueInvoice()) {
            events.add(EnrollmentPaymentEvent.ACCOUNT_SETTLED);
        }
        return events;
    }
}
