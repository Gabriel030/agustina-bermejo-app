package com.red_escuela.backend.billing.service;

import com.red_escuela.backend.billing.configuration.domain.ConfigurationVersionStatus;
import com.red_escuela.backend.billing.configuration.domain.YearBillingTerms;
import com.red_escuela.backend.billing.configuration.repository.ConfigurationVersionRepository;
import com.red_escuela.backend.billing.configuration.service.BillingConfigurationReader;
import com.red_escuela.backend.billing.dto.YearBillingConfigurationRequest;
import com.red_escuela.backend.billing.entity.CohortIntake;
import com.red_escuela.backend.billing.entity.ProgramYearBillingTemplate;
import com.red_escuela.backend.billing.repository.ProgramYearBillingTemplateRepository;
import com.red_escuela.backend.billing.usecase.cohort.UpdateCohortEntryFeeDiscountUseCase;
import com.red_escuela.backend.billing.usecase.cohort.UpdateYearBillingConfigurationUseCase;
import com.red_escuela.backend.shared.domain.Percentage;
import com.red_escuela.backend.shared.time.MutableClock;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class LegacyConfigurationMirrorIntegrationTest extends AbstractInvoiceSequenceTest {
    @Autowired
    private UpdateYearBillingConfigurationUseCase updateYearConfiguration;
    @Autowired
    private UpdateCohortEntryFeeDiscountUseCase updateEntryDiscount;
    @Autowired
    private ProgramYearBillingTemplateRepository templateRepository;
    @Autowired
    private BillingConfigurationReader reader;
    @Autowired
    private ConfigurationVersionRepository versionRepository;
    @Autowired
    private JdbcTemplate jdbc;

    private YearBillingTerms legacyTermsOf(Long templateId) {
        ProgramYearBillingTemplate t = templateRepository.findById(templateId).orElseThrow();
        return new YearBillingTerms(t.getRecurringFeeInvoicesCount(), t.getEnrollmentFeeCount(), t.getFirstEnrollmentFeeMonth(), t.getSecondEnrollmentFeeMonth(),
                t.getEnrollmentFeeLeadMonths(), t.getLateEnrollmentFeeDueDays(), t.getRecurringFeeDueDays(), t.isWithdrawSuspendedAtTermEnd(), t.isPaysWhilePaused(),
                t.isBlocksEnrollment(), t.isBlocksExamRegistration(), t.isBlocksCertificates(), t.isRequireEnrollmentFeeForFinalExamOnlyStudents());
    }

    private long versionsOfTopic(String scope, String topicPrefix, Long id) {
        return jdbc.queryForObject("select count(*) from configuration_version where scope = ? and topic_key like ?", Long.class, scope, topicPrefix + id + "%");
    }

    @Test
    @Tag("D-095")
    @Tag("D-002")
    void legacyTemplateEditIsMirroredAndReaderAgreesWithTheLegacyColumns() {
        Career career = newCareer();
        Year year = newYear(career, 2027, 10, 2, 2, 7);
        today(LocalDate.of(2027, 1, 10));

        updateYearConfiguration.update(year.templateId(), new YearBillingConfigurationRequest(null, null, null, null, null, 15, null, null, null, null, null, null));

        YearBillingTerms mirrored = reader.findYearTermsEffectiveOn(
                templateRepository.findProgramIdById(year.templateId()), templateRepository.findTermIdById(year.templateId()), LocalDate.now(MutableClock.INSTANCE)).orElseThrow();
        assertThat(mirrored).isEqualTo(legacyTermsOf(year.templateId()));
        assertThat(mirrored.recurringFeeDueDays()).isEqualTo(15);
    }

    @Test
    @Tag("D-095")
    void aNoopLegacyTemplateEditDoesNotCreateAnotherVersion() {
        Career career = newCareer();
        Year year = newYear(career, 2027, 10, 2, 2, 7);
        today(LocalDate.of(2027, 1, 10));
        YearBillingConfigurationRequest request = new YearBillingConfigurationRequest(null, null, null, null, null, 15, null, null, null, null, null, null);
        updateYearConfiguration.update(year.templateId(), request);
        Long programId = templateRepository.findProgramIdById(year.templateId());
        long before = versionsOfTopic("YEAR_TERMS", "YEAR_TERMS:P" + programId + ":T", templateRepository.findTermIdById(year.templateId()));

        updateYearConfiguration.update(year.templateId(), request);

        assertThat(versionsOfTopic("YEAR_TERMS", "YEAR_TERMS:P" + programId + ":T", templateRepository.findTermIdById(year.templateId()))).isEqualTo(before).isEqualTo(1);
    }

    @Test
    @Tag("D-212")
    @Tag("D-095")
    void legacyEntryDiscountEditIsMirroredAndANoopDoesNotDuplicate() {
        Career career = newCareer();
        Year year = newYear(career, 2027, 10, 2, 2, 7);
        Long cohortId = newCohort(year, CohortIntake.FIRST, LocalDate.of(2027, 3, 1));
        today(LocalDate.of(2027, 1, 10));
        Percentage fifteen = Percentage.ofFraction(new BigDecimal("0.15"));

        updateEntryDiscount.update(cohortId, fifteen);
        updateEntryDiscount.update(cohortId, fifteen);

        assertThat(reader.findCohortEntryEffectiveOn(cohortId, LocalDate.of(2027, 1, 10)).orElseThrow().discount()).isEqualTo(fifteen);
        assertThat(jdbc.queryForObject("select count(*) from configuration_version where scope = 'COHORT_ENTRY' and cohort_id = ?", Long.class, cohortId)).isEqualTo(1);
        assertThat(versionRepository.findAll().stream().filter(v -> cohortId.equals(v.getCohortId()))
                .allMatch(v -> v.getStatus() == ConfigurationVersionStatus.EFFECTIVE && v.isDirectPublication())).isTrue();
    }
}
