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
    private final Clock clock;

    public LegacyConfigurationMirror(ConfigurationVersionRepository repository, ConfigurationVersionActivator activator, Clock clock) {
        this.repository = repository;
        this.activator = activator;
        this.clock = clock;
    }

    public void recordYearTerms(ProgramYearBillingTemplate template, Long actorId) {
        YearBillingTerms terms = new YearBillingTerms(template.getRecurringFeeInvoicesCount(), template.getEnrollmentFeeCount(), template.getFirstEnrollmentFeeMonth(),
                template.getSecondEnrollmentFeeMonth(), template.getEnrollmentFeeLeadMonths(), template.getLateEnrollmentFeeDueDays(), template.getRecurringFeeDueDays(),
                template.isWithdrawSuspendedAtTermEnd(), template.isPaysWhilePaused(), template.isBlocksEnrollment(), template.isBlocksExamRegistration(),
                template.isBlocksCertificates(), template.isRequireEnrollmentFeeForFinalExamOnlyStudents());
        recordIfChanged(ConfigurationTopic.ofYearTerms(template.getProgram().getId(), template.getTerm().getId()), terms, LocalDate.now(clock), actorId);
    }

    public void recordCohortEntryDiscount(ProgramCohort cohort, Long actorId) {
        recordIfChanged(ConfigurationTopic.ofCohortEntry(cohort.getId()), new CohortEntryDiscount(cohort.getEntryFeeDiscount()), LocalDate.now(clock), actorId);
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
