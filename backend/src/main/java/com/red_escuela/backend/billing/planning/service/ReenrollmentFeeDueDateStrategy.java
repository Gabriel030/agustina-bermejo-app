package com.red_escuela.backend.billing.planning.service;

import com.red_escuela.backend.billing.planning.domain.SlotWindow;
import com.red_escuela.backend.billing.planning.policy.EnrollmentFeeSlotPlanningContext;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Optional;

public final class ReenrollmentFeeDueDateStrategy implements EnrollmentFeeDueDateStrategy {

    public boolean appliesTo(EnrollmentFeeSlotPlanningContext context) {
        return true;
    }

    public YearMonth periodOf(EnrollmentFeeSlotPlanningContext context) {
        return context.cycle().slotPeriod(context.slot());
    }

    public LocalDate dueDateOf(EnrollmentFeeSlotPlanningContext context) {
        LocalDate regularDue = Optional.ofNullable(context.facts().windows().get(SlotWindow.key(context.cycle().templateId(), context.slot())))
                .map(SlotWindow::closesOn)
                .orElseGet(() -> periodOf(context).atDay(1));
        return regularDue.isBefore(context.facts().today())
                ? context.facts().issueDate().plusDays(context.terms().lateEnrollmentFeeDueDays())
                : regularDue;
    }
}
