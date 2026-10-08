package com.red_escuela.backend.billing.configuration.service;

import com.red_escuela.backend.billing.configuration.policy.ProgramYearBillingTemplateSnapshot;
import com.red_escuela.backend.billing.configuration.domain.YearBillingTerms;
import com.red_escuela.backend.billing.configuration.policy.YearBillingConfigurationChange;
import com.red_escuela.backend.billing.dto.YearBillingConfigurationRequest;
import com.red_escuela.backend.billing.entity.ProgramYearBillingTemplate;
import com.red_escuela.backend.billing.repository.InvoiceRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

@Component
public class YearBillingConfigurationChangeLoader {
    private final InvoiceRepository invoiceRepository;

    public YearBillingConfigurationChangeLoader(InvoiceRepository invoiceRepository) {
        this.invoiceRepository = invoiceRepository;
    }

    public YearBillingConfigurationChange loadConfigurationUpdate(ProgramYearBillingTemplate template, YearBillingConfigurationRequest request) {
        return loadChange(template, snapshotOf(template, request), template.termStartDate(), template.termEndDate());
    }

    public YearBillingConfigurationChange loadVersionedChange(ProgramYearBillingTemplate template, YearBillingTerms proposed) {
        ProgramYearBillingTemplateSnapshot snapshot = new ProgramYearBillingTemplateSnapshot(proposed.enrollmentFeeCount(), proposed.firstEnrollmentFeeMonth(),
                proposed.secondEnrollmentFeeMonth(), proposed.recurringFeeInvoicesCount(), proposed.enrollmentFeeLeadMonths());
        return loadChange(template, snapshot, template.termStartDate(), template.termEndDate());
    }

    public YearBillingConfigurationChange loadTermScheduleChange(ProgramYearBillingTemplate template, LocalDateTime proposedStart, LocalDateTime proposedEnd) {
        return loadChange(template, snapshotOf(template, null), proposedStart, proposedEnd);
    }

    public YearBillingConfigurationChange loadCreation(LocalDateTime termStart, LocalDateTime termEnd, Integer recurringFeeInvoicesCount, YearBillingConfigurationRequest request) {
        ProgramYearBillingTemplate defaults = new ProgramYearBillingTemplate();
        ProgramYearBillingTemplateSnapshot proposed = merge(snapshotOf(defaults, null), request, recurringFeeInvoicesCount);
        return YearBillingConfigurationChange.forCreation(proposed, monthOf(termStart), monthOf(termEnd));
    }

    private YearBillingConfigurationChange loadChange(ProgramYearBillingTemplate template, ProgramYearBillingTemplateSnapshot proposed,
                                                      LocalDateTime proposedStart, LocalDateTime proposedEnd) {
        Set<Integer> issuedSlots = new HashSet<>(invoiceRepository.findIssuedEnrollmentFeeSlotsByTemplateId(template.getId()));
        Set<YearMonth> issuedPeriods = new HashSet<>();
        if (template.termStartDate() != null && template.termEndDate() != null) {
            issuedPeriods.addAll(invoiceRepository.findIssuedInstallmentPeriodsByProgramIdAndPeriodBetween(
                    template.programId(), YearMonth.from(template.termStartDate()), YearMonth.from(template.termEndDate())));
        }
        return new YearBillingConfigurationChange(snapshotOf(template, null), proposed, monthOf(proposedStart), monthOf(proposedEnd), issuedSlots, issuedPeriods);
    }

    private ProgramYearBillingTemplateSnapshot snapshotOf(ProgramYearBillingTemplate template, YearBillingConfigurationRequest request) {
        ProgramYearBillingTemplateSnapshot current = new ProgramYearBillingTemplateSnapshot(template.getEnrollmentFeeCount(),
                template.getFirstEnrollmentFeeMonth(), template.getSecondEnrollmentFeeMonth(), template.getRecurringFeeInvoicesCount(),
                template.getEnrollmentFeeLeadMonths());
        return request == null ? current : merge(current, request, null);
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
