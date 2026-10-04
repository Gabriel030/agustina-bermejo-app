package com.red_escuela.backend.billing.planning;

import com.red_escuela.backend.billing.entity.PaymentPlanCadence;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Set;
import java.util.function.Predicate;

public record CourseChargeFacts(Long enrollmentId, YearMonth month, LocalDate today, boolean enrolled, boolean suspendedOrWithdrawn,
                                boolean hasPaymentPlan, PaymentPlanCadence cadence, YearMonth upfrontFrom, Integer upfrontScopeMonths,
                                Predicate<YearMonth> termContains, Integer durationMonths, Set<YearMonth> issuedPeriods, long issuedInTerm,
                                int recurringFeeDueDays) {
}
