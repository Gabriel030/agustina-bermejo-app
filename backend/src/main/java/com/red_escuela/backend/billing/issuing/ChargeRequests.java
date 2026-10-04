package com.red_escuela.backend.billing.issuing;

import com.red_escuela.backend.academicstructure.entity.Course;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.billing.entity.FeeType;
import com.red_escuela.backend.billing.entity.IssueReason;
import com.red_escuela.backend.billing.entity.ProgramCohort;
import com.red_escuela.backend.billing.entity.ProgramYearBillingTemplate;
import com.red_escuela.backend.billing.planning.YearCycle;

import java.time.LocalDate;
import java.time.YearMonth;

public final class ChargeRequests {

    private ChargeRequests() {
    }

    public static ChargeRequest programAdmission(Program program, ProgramCohort cohort, LocalDate today) {
        ProgramYearBillingTemplate template = cohort.getTemplate();
        YearMonth entryMonth = YearMonth.from(cohort.getCursadaStart());
        int slot = YearCycle.of(template, entryMonth.getYear()).slotOf(entryMonth);
        return new ChargeRequest(new ChargeTarget.ProgramApplicant(program, cohort), FeeType.ENROLLMENT_FEE, entryMonth, today,
                IssueReason.ADMISSION, null, template.getId(), slot, true, null, null, null, null);
    }

    public static ChargeRequest courseAdmission(Course course, LocalDate today) {
        return new ChargeRequest(new ChargeTarget.CourseApplicant(course), FeeType.RECURRING_FEE, YearMonth.from(today), today,
                IssueReason.COURSE_ADMISSION, null, null, null, false, null, null, null, null);
    }
}
