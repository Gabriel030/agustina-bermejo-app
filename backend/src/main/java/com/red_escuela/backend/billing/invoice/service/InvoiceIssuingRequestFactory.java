package com.red_escuela.backend.billing.invoice.service;

import com.red_escuela.backend.academicstructure.entity.Course;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.billing.invoice.domain.InvoiceIssuingReason;
import com.red_escuela.backend.billing.entity.ProgramCohort;
import com.red_escuela.backend.billing.entity.ProgramYearBillingTemplate;
import com.red_escuela.backend.billing.planning.domain.YearCycle;

import java.time.LocalDate;
import java.time.YearMonth;
import com.red_escuela.backend.billing.invoice.domain.InvoiceIssuingRequest;
import com.red_escuela.backend.billing.invoice.domain.InvoiceRecipient;

public final class InvoiceIssuingRequestFactory {

    private InvoiceIssuingRequestFactory() {
    }

    public static InvoiceIssuingRequest createProgramAdmissionRequest(Program program, ProgramCohort cohort, LocalDate today) {
        ProgramYearBillingTemplate template = cohort.getTemplate();
        YearMonth entryMonth = YearMonth.from(cohort.getCursadaStart());
        int slot = YearCycle.of(template, entryMonth.getYear()).slotOf(entryMonth);
        return new InvoiceIssuingRequest(new InvoiceRecipient.ProgramApplicantRecipient(program, cohort), FeeType.ENROLLMENT_FEE, entryMonth, today,
                InvoiceIssuingReason.PROGRAM_ADMISSION, null, template.getId(), slot, true, null, null, null, null);
    }

    public static InvoiceIssuingRequest createCourseAdmissionRequest(Course course, LocalDate today) {
        return new InvoiceIssuingRequest(new InvoiceRecipient.CourseApplicantRecipient(course), FeeType.MONTHLY_INSTALLMENT, YearMonth.from(today), today,
                InvoiceIssuingReason.COURSE_ADMISSION, null, null, null, false, null, null, null, null);
    }
}
