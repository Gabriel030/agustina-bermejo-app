package com.red_escuela.backend.billing.invoice.service;

import com.red_escuela.backend.billing.entity.PaymentPlanSelection;
import com.red_escuela.backend.billing.entity.ProgramCohort;
import com.red_escuela.backend.billing.entity.ProgramYearBillingTemplate;
import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.billing.invoice.domain.InvoiceIssuingReason;
import com.red_escuela.backend.billing.invoice.domain.issuing.InvoiceIssuingRequest;
import com.red_escuela.backend.billing.planning.domain.PlannedInvoice;
import com.red_escuela.backend.billing.planning.domain.YearCycle;
import com.red_escuela.backend.academicstructure.entity.Course;
import com.red_escuela.backend.enrollment.entity.CourseEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import com.red_escuela.backend.shared.domain.statemachine.StatusChangeActor;

import java.time.LocalDate;
import java.time.YearMonth;

public final class InvoiceIssuingRequestFactory {

    private static final String PROCESS_NAME = "billing";

    private InvoiceIssuingRequestFactory() {
    }

    public static InvoiceIssuingRequest createProgramAdmissionRequest(Long studentId, Program program, ProgramCohort cohort, LocalDate today) {
        ProgramYearBillingTemplate template = cohort.getTemplate();
        YearMonth entryMonth = YearMonth.from(cohort.getCursadaStart());
        int slot = YearCycle.of(template, entryMonth.getYear()).slotOf(entryMonth);
        return new InvoiceIssuingRequest(studentId, null, null, program.getId(), null, cohort.getId(), FeeType.ENROLLMENT_FEE, entryMonth, today.plusDays(template.getLateEnrollmentFeeDueDays()), today,
                InvoiceIssuingReason.PROGRAM_ADMISSION, null, template.getId(), slot, true, null, null, null, null, null, actor());
    }

    public static InvoiceIssuingRequest createCourseAdmissionRequest(Long studentId, Course course, LocalDate today) {
        return new InvoiceIssuingRequest(studentId, null, null, null, course.getId(), null, FeeType.MONTHLY_INSTALLMENT, YearMonth.from(today), today, today,
                InvoiceIssuingReason.COURSE_ADMISSION, null, null, null, false, null, null, null, null, null, actor());
    }

    public static InvoiceIssuingRequest createProgramEnrollmentRequest(ProgramEnrollment enrollment, PaymentPlanSelection selection, PlannedInvoice planned,
                                                                       InvoiceIssuingReason reason) {
        return new InvoiceIssuingRequest(enrollment.getStudent().getId(), enrollment.getId(), null, enrollment.getProgram().getId(), null,
                enrollment.getProgramCohort() == null ? null : enrollment.getProgramCohort().getId(),
                planned.kind(), planned.period(), planned.dueDate(), planned.priceDate(), reason, selection == null ? null : selection.getId(),
                planned.templateId(), planned.slot(), planned.entry(), null, null, null, null, null, actor());
    }

    public static InvoiceIssuingRequest createCourseEnrollmentRequest(CourseEnrollment enrollment, PaymentPlanSelection selection, PlannedInvoice planned,
                                                                      InvoiceIssuingReason reason) {
        return new InvoiceIssuingRequest(enrollment.getStudent().getId(), null, enrollment.getId(), null, enrollment.getCourse().getId(), null,
                FeeType.MONTHLY_INSTALLMENT, planned.period(), planned.dueDate(), planned.priceDate(), reason, selection == null ? null : selection.getId(),
                null, null, false, null, null, null, null, null, actor());
    }

    public static InvoiceIssuingRequest createProgramAdHocRequest(ProgramEnrollment enrollment, FeeType feeType, LocalDate dueDate, NonNegativeMoney amount,
                                                                  String description, Long subjectId, Long examBoardRegistrationId, String idempotencyKey) {
        return new InvoiceIssuingRequest(enrollment.getStudent().getId(), enrollment.getId(), null, enrollment.getProgram().getId(), null, null, feeType,
                YearMonth.from(dueDate), dueDate, dueDate, InvoiceIssuingReason.AD_HOC_CHARGE, null, null, null, false, amount, description, subjectId,
                examBoardRegistrationId, idempotencyKey, actor());
    }

    public static InvoiceIssuingRequest createCourseAdHocRequest(CourseEnrollment enrollment, FeeType feeType, LocalDate dueDate, NonNegativeMoney amount,
                                                                 String description, Long subjectId, Long examBoardRegistrationId, String idempotencyKey) {
        return new InvoiceIssuingRequest(enrollment.getStudent().getId(), null, enrollment.getId(), null, enrollment.getCourse().getId(), null, feeType,
                YearMonth.from(dueDate), dueDate, dueDate, InvoiceIssuingReason.AD_HOC_CHARGE, null, null, null, false, amount, description, subjectId,
                examBoardRegistrationId, idempotencyKey, actor());
    }

    private static StatusChangeActor actor() {
        return new StatusChangeActor(null, PROCESS_NAME);
    }
}
