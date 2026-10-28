package com.red_escuela.backend.billing.configuration.service;

import com.red_escuela.backend.billing.configuration.domain.AllocationSettings;
import com.red_escuela.backend.billing.configuration.domain.AnnualUpfrontSettings;
import com.red_escuela.backend.billing.configuration.domain.BillingDashboardGoals;
import com.red_escuela.backend.billing.configuration.domain.CohortEntryDiscount;
import com.red_escuela.backend.billing.configuration.domain.CollectionReminderSettings;
import com.red_escuela.backend.billing.configuration.domain.ConfigurationTopic;
import com.red_escuela.backend.billing.configuration.domain.CourseReminderSchedule;
import com.red_escuela.backend.billing.configuration.domain.CourseTerms;
import com.red_escuela.backend.billing.configuration.domain.IrregularTermTypes;
import com.red_escuela.backend.billing.configuration.domain.IssuanceSettings;
import com.red_escuela.backend.billing.configuration.domain.LatePaymentTerms;
import com.red_escuela.backend.billing.configuration.domain.ProgramReminderSchedule;
import com.red_escuela.backend.billing.configuration.domain.ReminderSchedule;
import com.red_escuela.backend.billing.configuration.domain.ReminderScheduleProposal;
import com.red_escuela.backend.billing.configuration.domain.ReminderScheduleTarget;
import com.red_escuela.backend.billing.configuration.domain.TeachingGroup;
import com.red_escuela.backend.billing.configuration.domain.YearBillingTerms;
import com.red_escuela.backend.billing.configuration.policy.ChangeBillingConfigurationCommand;
import com.red_escuela.backend.billing.operation.AuthorizationRequestDescription.ChangeRow;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

@Component
public class BillingConfigurationChangeRowsAssembler {
    private final BillingConfigurationReader reader;
    private final Clock clock;

    public BillingConfigurationChangeRowsAssembler(BillingConfigurationReader reader, Clock clock) {
        this.reader = reader;
        this.clock = clock;
    }

    public List<ChangeRow> assembleRows(ChangeBillingConfigurationCommand command) {
        LocalDate today = LocalDate.now(clock);
        ConfigurationTopic topic = command.topic();
        return switch (command.values()) {
            case YearBillingTerms proposed -> ConfigurationValuesChangeRows.buildYearTermsRows(
                    reader.findYearTermsEffectiveOn(topic.programId(), topic.termId(), today).orElse(null), proposed);
            case CohortEntryDiscount proposed -> ConfigurationValuesChangeRows.buildCohortEntryRows(
                    new CohortEntryDiscount(reader.cohortEntryDiscountEffectiveOn(topic.cohortId(), today)), proposed);
            case LatePaymentTerms proposed -> ConfigurationValuesChangeRows.buildLatePaymentRows(reader.findLatePaymentSettingsEffectiveOn(today).orElse(null), proposed);
            case IssuanceSettings proposed -> ConfigurationValuesChangeRows.buildIssuanceRows(reader.findIssuanceSettingsEffectiveOn(today).orElse(null), proposed);
            case AllocationSettings proposed -> ConfigurationValuesChangeRows.buildAllocationRows(reader.findAllocationSettingsEffectiveOn(today).orElse(null), proposed);
            case AnnualUpfrontSettings proposed -> ConfigurationValuesChangeRows.buildAnnualUpfrontRows(
                    reader.findAnnualUpfrontSettingsEffectiveOn(topic.programId(), topic.termId(), today).orElse(null), proposed);
            case CourseTerms proposed -> ConfigurationValuesChangeRows.buildCourseTermsRows(reader.courseTermsEffectiveOn(topic.courseId(), today), proposed);
            case IrregularTermTypes proposed -> ConfigurationValuesChangeRows.buildIrregularTermsRows(reader.irregularTermTypesEffectiveOn(today), proposed);
            case CollectionReminderSettings proposed -> ConfigurationValuesChangeRows.buildCollectionReminderSettingsRows(
                    reader.collectionReminderSettingsEffectiveOn(today), proposed);
            case BillingDashboardGoals proposed -> ConfigurationValuesChangeRows.buildDashboardGoalsRows(reader.billingDashboardGoalsEffectiveOn(today), proposed);
            case ProgramReminderSchedule proposed -> assembleScheduleRows(new ReminderScheduleTarget(TeachingGroup.PROGRAMS, topic.programId()), proposed.schedule(), today);
            case CourseReminderSchedule proposed -> assembleScheduleRows(new ReminderScheduleTarget(TeachingGroup.MONTHLY_COURSES, topic.courseId()), proposed.schedule(), today);
        };
    }

    private List<ChangeRow> assembleScheduleRows(ReminderScheduleTarget target, ReminderSchedule proposed, LocalDate today) {
        return ReminderScheduleChangeRows.buildScheduleRows(reader.reminderScheduleFor(target, today),
                reader.reminderScheduleWithProposal(target, today, new ReminderScheduleProposal(target, proposed, null)));
    }
}
