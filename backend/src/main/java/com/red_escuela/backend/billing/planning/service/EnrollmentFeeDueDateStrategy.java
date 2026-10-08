package com.red_escuela.backend.billing.planning.service;

import com.red_escuela.backend.billing.planning.policy.EnrollmentFeeSlotPlanningContext;

import java.time.LocalDate;
import java.time.YearMonth;

public interface EnrollmentFeeDueDateStrategy {
    boolean appliesTo(EnrollmentFeeSlotPlanningContext context);

    YearMonth periodOf(EnrollmentFeeSlotPlanningContext context);

    LocalDate dueDateOf(EnrollmentFeeSlotPlanningContext context);
}
