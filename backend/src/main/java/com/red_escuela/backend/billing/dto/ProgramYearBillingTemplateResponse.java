package com.red_escuela.backend.billing.dto;

import com.red_escuela.backend.billing.configuration.domain.YearBillingTerms;
import com.red_escuela.backend.billing.configuration.domain.AdvanceEnrollmentFeeAlerts;
import com.red_escuela.backend.billing.entity.ProgramYearBillingTemplate;

import org.springframework.lang.Nullable;

import java.time.LocalDate;

public record ProgramYearBillingTemplateResponse(Long id, Long programId, Long termId, Integer recurringFeeInvoicesCount,
                                                 Integer enrollmentFeeCount, Integer firstEnrollmentFeeMonth, Integer secondEnrollmentFeeMonth,
                                                 Integer enrollmentFeeLeadMonths, Integer lateEnrollmentFeeDueDays, Integer recurringFeeDueDays, boolean withdrawSuspendedAtTermEnd, boolean paysWhilePaused, boolean blocksEnrollment, boolean blocksExamRegistration,
                                                 boolean blocksCertificates, boolean requireEnrollmentFeeForFinalExamOnlyStudents,
                                                 @Nullable LocalDate advanceEnrollmentFeeOpensOn, boolean advanceOpeningAfterRegularEmission, boolean nextYearPriceMissing) {

    public static ProgramYearBillingTemplateResponse from(ProgramYearBillingTemplate template, YearBillingTerms terms, AdvanceEnrollmentFeeAlerts alerts) {
        return new ProgramYearBillingTemplateResponse(
                template.getId(), template.getProgram().getId(), template.termId(), terms.recurringFeeInvoicesCount(),
                terms.enrollmentFeeCount(), terms.firstEnrollmentFeeMonth(), terms.secondEnrollmentFeeMonth(),
                terms.enrollmentFeeLeadMonths(), terms.lateEnrollmentFeeDueDays(), terms.recurringFeeDueDays(), terms.withdrawSuspendedAtTermEnd(), terms.paysWhilePaused(), terms.blocksEnrollment(), terms.blocksExamRegistration(),
                terms.blocksCertificates(), terms.requireEnrollmentFeeForFinalExamOnlyStudents(),
                terms.advanceEnrollmentFeeOpensOn(), alerts.advanceOpeningAfterRegularEmission(), alerts.nextYearPriceMissing());
    }
}
