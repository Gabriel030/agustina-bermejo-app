package com.red_escuela.backend.billing.service;

import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.billing.exception.InvalidEnrollmentFeeConfigurationException;
import com.red_escuela.backend.billing.planning.YearCycle;
import java.time.YearMonth;
import com.red_escuela.backend.billing.dto.YearBillingConfigurationRequest;
import com.red_escuela.backend.billing.entity.ProgramYearBillingTemplate;
import org.springframework.stereotype.Component;

@Component
public class YearBillingConfigurationApplier {

    public void apply(ProgramYearBillingTemplate template, YearBillingConfigurationRequest request) {
        if (request == null) {
            return;
        }
        int count = request.enrollmentFeeCount() != null ? request.enrollmentFeeCount() : template.getEnrollmentFeeCount();
        int first = request.firstEnrollmentFeeMonth() != null ? request.firstEnrollmentFeeMonth() : template.getFirstEnrollmentFeeMonth();
        Integer second = request.secondEnrollmentFeeMonth() != null ? request.secondEnrollmentFeeMonth() : template.getSecondEnrollmentFeeMonth();
        template.configureEnrollmentFees(count, first, second);
        validateAgainstTerm(template);
        if (request.enrollmentFeeLeadMonths() != null) {
            template.setEnrollmentFeeLeadMonths(request.enrollmentFeeLeadMonths());
        }
        if (request.lateEnrollmentFeeDueDays() != null) {
            template.setLateEnrollmentFeeDueDays(request.lateEnrollmentFeeDueDays());
        }
        if (request.recurringFeeDueDays() != null) {
            template.setRecurringFeeDueDays(request.recurringFeeDueDays());
        }
        if (request.withdrawSuspendedAtTermEnd() != null) {
            template.setWithdrawSuspendedAtTermEnd(request.withdrawSuspendedAtTermEnd());
        }
        if (request.paysWhilePaused() != null) {
            template.setPaysWhilePaused(request.paysWhilePaused());
        }
        if (request.blocksEnrollment() != null) {
            template.setBlocksEnrollment(request.blocksEnrollment());
        }
        if (request.blocksExamRegistration() != null) {
            template.setBlocksExamRegistration(request.blocksExamRegistration());
        }
        if (request.blocksCertificates() != null) {
            template.setBlocksCertificates(request.blocksCertificates());
        }
        if (request.requireEnrollmentFeeForFinalExamOnlyStudents() != null) {
            template.setRequireEnrollmentFeeForFinalExamOnlyStudents(request.requireEnrollmentFeeForFinalExamOnlyStudents());
        }
    }

    private void validateAgainstTerm(ProgramYearBillingTemplate template) {
        Term term = template.getTerm();
        if (term == null || term.getStartDate() == null || term.getEndDate() == null) {
            return;
        }
        YearCycle cycle = YearCycle.of(template, term.getStartDate().getYear());
        YearMonth termStart = YearMonth.from(term.getStartDate());
        YearMonth termEnd = YearMonth.from(term.getEndDate());
        if (cycle.slotPeriod(1).isAfter(termStart)) {
            throw new InvalidEnrollmentFeeConfigurationException("La primera matrícula debe ser anterior o igual al inicio del año lectivo");
        }
        if (template.getEnrollmentFeeCount() == 2) {
            YearMonth second = cycle.slotPeriod(2);
            if (!second.isAfter(termStart) || second.isAfter(termEnd)) {
                throw new InvalidEnrollmentFeeConfigurationException("La segunda matrícula debe caer dentro del año lectivo");
            }
        }
    }
}
