package com.red_escuela.backend.billing.configuration.service;

import com.red_escuela.backend.billing.configuration.domain.CohortEntryDiscount;
import com.red_escuela.backend.billing.configuration.domain.ConfigurationScope;
import com.red_escuela.backend.billing.configuration.domain.ConfigurationTopic;
import com.red_escuela.backend.billing.configuration.domain.ConfigurationValues;
import com.red_escuela.backend.billing.configuration.domain.ConfigurationVersion;
import com.red_escuela.backend.billing.configuration.domain.ConfigurationVersionStatus;
import com.red_escuela.backend.billing.configuration.domain.LatePaymentTerms;
import com.red_escuela.backend.billing.configuration.domain.YearBillingTerms;
import com.red_escuela.backend.billing.configuration.repository.ConfigurationVersionRepository;
import com.red_escuela.backend.billing.entity.LatePaymentSettings;
import com.red_escuela.backend.billing.entity.ProgramCohort;
import com.red_escuela.backend.billing.entity.ProgramYearBillingTemplate;
import com.red_escuela.backend.billing.repository.ProgramCohortRepository;
import com.red_escuela.backend.billing.repository.ProgramYearBillingTemplateRepository;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;

@Component
public class LegacyConfigurationMirror {
    private static final String MIRROR_REASON = "Cambio registrado por el circuito anterior";
    private static final int INITIAL_LATE_FEE_CAP_MONTHS = 12;
    private static final int LATE_FEE_DEBIT_NOTE_DUE_DAYS = 10;

    private final ConfigurationVersionRepository repository;
    private final ConfigurationVersionActivator activator;
    private final ProgramYearBillingTemplateRepository templateRepository;
    private final ProgramCohortRepository cohortRepository;
    private final BillingActorResolver actorResolver;
    private final Clock clock;

    public LegacyConfigurationMirror(ConfigurationVersionRepository repository, ConfigurationVersionActivator activator,
                                  ProgramYearBillingTemplateRepository templateRepository, ProgramCohortRepository cohortRepository,
                                  BillingActorResolver actorResolver, Clock clock) {
        this.templateRepository = templateRepository;
        this.cohortRepository = cohortRepository;
        this.actorResolver = actorResolver;
        this.repository = repository;
        this.activator = activator;
        this.clock = clock;
    }

    public void recordYearTerms(ProgramYearBillingTemplate template) {
        YearBillingTerms terms = new YearBillingTerms(template.getRecurringFeeInvoicesCount(), template.getEnrollmentFeeCount(), template.getFirstEnrollmentFeeMonth(),
                template.getSecondEnrollmentFeeMonth(), template.getEnrollmentFeeLeadMonths(), template.getLateEnrollmentFeeDueDays(), template.getRecurringFeeDueDays(),
                template.isWithdrawSuspendedAtTermEnd(), template.isPaysWhilePaused(), template.isBlocksEnrollment(), template.isBlocksExamRegistration(),
                template.isBlocksCertificates(), template.isRequireEnrollmentFeeForFinalExamOnlyStudents());
        recordIfChanged(ConfigurationTopic.ofYearTerms(templateRepository.findProgramIdById(template.getId()), templateRepository.findTermIdById(template.getId())), terms,
                LocalDate.now(clock), actorResolver.currentPersonIdOr(templateRepository.findCreatedByIdById(template.getId())));
    }

    public void recordCohortEntryDiscount(ProgramCohort cohort) {
        recordIfChanged(ConfigurationTopic.ofCohortEntry(cohort.getId()), new CohortEntryDiscount(cohort.getEntryFeeDiscount()), LocalDate.now(clock),
                actorResolver.currentPersonIdOr(cohortRepository.findCreatedByIdById(cohort.getId())));
    }

    public void recordLatePayment(LatePaymentSettings settings, Long actorId) {
        LatePaymentTerms terms = new LatePaymentTerms(settings.getGracePeriodDays(), settings.getSurchargeRate(), INITIAL_LATE_FEE_CAP_MONTHS, null,
                settings.getSuspensionAfterDays(), settings.getSuspensionAfterInvoiceCount(), true, LATE_FEE_DEBIT_NOTE_DUE_DAYS, true);
        recordIfChanged(ConfigurationTopic.ofGlobal(ConfigurationScope.LATE_PAYMENT), terms,
                settings.getEffectiveFrom(), actorId);
    }

    private void recordIfChanged(ConfigurationTopic topic, ConfigurationValues values, LocalDate effectiveFrom, Long actorId) {
        var current = repository.findEffectiveVersion(topic.scope().name(), topic.key(), effectiveFrom);
        if (current.filter(version -> version.values().equals(values)).isPresent()) {
            return;
        }
        LocalDate today = LocalDate.now(clock);
        ConfigurationVersion published = repository.saveAndFlush(ConfigurationVersion.publish(topic, values, effectiveFrom, MIRROR_REASON, actorId, today,
                current.map(ConfigurationVersion::getId).orElse(null), Instant.now(clock)));
        if (published.getStatus() == ConfigurationVersionStatus.EFFECTIVE) {
            activator.supersedeOlderEffectiveVersions(published);
        }
    }
}
