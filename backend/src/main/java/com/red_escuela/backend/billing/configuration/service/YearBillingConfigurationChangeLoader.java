package com.red_escuela.backend.billing.configuration.service;

import com.red_escuela.backend.billing.configuration.policy.ProgramYearBillingTemplateSnapshot;
import com.red_escuela.backend.billing.configuration.domain.YearBillingTerms;
import com.red_escuela.backend.billing.configuration.policy.YearBillingConfigurationChange;
import com.red_escuela.backend.billing.dto.YearBillingConfigurationRequest;
import com.red_escuela.backend.billing.entity.ProgramYearBillingTemplate;
import com.red_escuela.backend.billing.invoice.service.IssuedChargeQueryService;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

@Component
public class YearBillingConfigurationChangeLoader {
    private final IssuedChargeQueryService issuedChargeQueryService;
    private final BillingConfigurationReader configurationReader;
    private final Clock clock;

    public YearBillingConfigurationChangeLoader(IssuedChargeQueryService issuedChargeQueryService, BillingConfigurationReader configurationReader, Clock clock) {
        this.issuedChargeQueryService = issuedChargeQueryService;
        this.configurationReader = configurationReader;
        this.clock = clock;
    }

    public YearBillingConfigurationChange loadVersionedChange(ProgramYearBillingTemplate template, YearBillingTerms proposed) {
        YearBillingTerms current = currentTermsOf(template);
        ProgramYearBillingTemplateSnapshot snapshot = new ProgramYearBillingTemplateSnapshot(proposed.enrollmentFeeCount(), proposed.firstEnrollmentFeeMonth(),
                proposed.secondEnrollmentFeeMonth(), proposed.recurringFeeInvoicesCount(), proposed.enrollmentFeeLeadMonths());
        return loadChange(template, snapshotOf(current), snapshot, template.termStartDate(), template.termEndDate());
    }

    public YearBillingConfigurationChange loadTermScheduleChange(ProgramYearBillingTemplate template, LocalDateTime proposedStart, LocalDateTime proposedEnd) {
        ProgramYearBillingTemplateSnapshot current = snapshotOf(currentTermsOf(template));
        return loadChange(template, current, current, proposedStart, proposedEnd);
    }

    public YearBillingConfigurationChange loadCreation(LocalDateTime termStart, LocalDateTime termEnd, Integer recurringFeeInvoicesCount, YearBillingConfigurationRequest request) {
        ProgramYearBillingTemplateSnapshot proposed = merge(snapshotOf(YearBillingTerms.initial(recurringFeeInvoicesCount)), request, recurringFeeInvoicesCount);
        return YearBillingConfigurationChange.forCreation(proposed, monthOf(termStart), monthOf(termEnd));
    }

    private YearBillingConfigurationChange loadChange(ProgramYearBillingTemplate template, ProgramYearBillingTemplateSnapshot current, ProgramYearBillingTemplateSnapshot proposed,
                                                      LocalDateTime proposedStart, LocalDateTime proposedEnd) {
        Set<Integer> issuedSlots = new HashSet<>(issuedChargeQueryService.findIssuedEnrollmentFeeSlots(template.getId()));
        Set<YearMonth> issuedPeriods = new HashSet<>();
        if (template.termStartDate() != null && template.termEndDate() != null) {
            issuedPeriods.addAll(issuedChargeQueryService.findIssuedInstallmentPeriods(
                    template.programId(), YearMonth.from(template.termStartDate()), YearMonth.from(template.termEndDate())));
        }
        return new YearBillingConfigurationChange(current, proposed, monthOf(proposedStart), monthOf(proposedEnd), issuedSlots, issuedPeriods);
    }

    private YearBillingTerms currentTermsOf(ProgramYearBillingTemplate template) {
        return configurationReader.requireYearTermsEffectiveOn(template.programId(), template.termId(), LocalDate.now(clock));
    }

    private ProgramYearBillingTemplateSnapshot snapshotOf(YearBillingTerms terms) {
        return new ProgramYearBillingTemplateSnapshot(terms.enrollmentFeeCount(), terms.firstEnrollmentFeeMonth(), terms.secondEnrollmentFeeMonth(),
                terms.recurringFeeInvoicesCount(), terms.enrollmentFeeLeadMonths());
    }

    private ProgramYearBillingTemplateSnapshot merge(ProgramYearBillingTemplateSnapshot base, YearBillingConfigurationRequest request, Integer recurringFeeInvoicesCount) {
        int count = request != null && request.enrollmentFeeCount() != null ? request.enrollmentFeeCount() : base.enrollmentFeeCount();
        int first = request != null && request.firstEnrollmentFeeMonth() != null ? request.firstEnrollmentFeeMonth() : base.firstEnrollmentFeeMonth();
        Integer second = request != null && request.secondEnrollmentFeeMonth() != null ? request.secondEnrollmentFeeMonth() : base.secondEnrollmentFeeMonth();
        Integer installments = recurringFeeInvoicesCount != null ? recurringFeeInvoicesCount : base.recurringFeeInvoicesCount();
        int leadMonths = request != null && request.enrollmentFeeLeadMonths() != null ? request.enrollmentFeeLeadMonths() : base.enrollmentFeeLeadMonths();
        return new ProgramYearBillingTemplateSnapshot(count, first, count == 1 ? null : second, installments, leadMonths);
    }

    private Optional<YearMonth> monthOf(LocalDateTime date) {
        return Optional.ofNullable(date).map(YearMonth::from);
    }
}
