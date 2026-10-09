package com.red_escuela.backend.billing.planning;

import com.red_escuela.backend.billing.configuration.domain.YearBillingTerms;
import com.red_escuela.backend.billing.entity.ProgramYearBillingTemplate;
import com.red_escuela.backend.billing.planning.domain.YearCycle;

import com.red_escuela.backend.billing.configuration.domain.CohortEntryDiscount;
import com.red_escuela.backend.billing.configuration.domain.ConfigurationTopic;
import com.red_escuela.backend.billing.configuration.domain.ConfigurationVersion;
import com.red_escuela.backend.billing.entity.ProgramCohort;
import com.red_escuela.backend.shared.domain.Percentage;
import jakarta.persistence.EntityManager;

import java.time.Instant;
import java.time.LocalDate;
import java.util.IdentityHashMap;
import java.util.Map;

public final class TestTemplateTerms {
    private static final Map<ProgramYearBillingTemplate, YearBillingTerms> TERMS = new IdentityHashMap<>();

    private TestTemplateTerms() {
    }

    public static synchronized YearBillingTerms termsOf(ProgramYearBillingTemplate template) {
        return TERMS.computeIfAbsent(template, key -> YearBillingTerms.initial(0));
    }

    public static synchronized void replace(ProgramYearBillingTemplate template, YearBillingTerms terms) {
        TERMS.put(template, terms);
    }

    public static YearCycle cycleOf(ProgramYearBillingTemplate template, int fallbackYear) {
        return YearCycle.of(template, termsOf(template), fallbackYear);
    }

    public static void configureEnrollmentFees(ProgramYearBillingTemplate template, int count, int first, Integer second) {
        YearBillingTerms t = termsOf(template);
        replace(template, new YearBillingTerms(t.recurringFeeInvoicesCount(), count, first, count == 1 ? null : second, t.enrollmentFeeLeadMonths(), t.lateEnrollmentFeeDueDays(),
                t.recurringFeeDueDays(), t.withdrawSuspendedAtTermEnd(), t.paysWhilePaused(), t.blocksEnrollment(), t.blocksExamRegistration(), t.blocksCertificates(),
                t.requireEnrollmentFeeForFinalExamOnlyStudents()));
    }

    public static void setRecurringFeeInvoicesCount(ProgramYearBillingTemplate template, int value) {
        YearBillingTerms t = termsOf(template);
        replace(template, new YearBillingTerms(value, t.enrollmentFeeCount(), t.firstEnrollmentFeeMonth(), t.secondEnrollmentFeeMonth(), t.enrollmentFeeLeadMonths(),
                t.lateEnrollmentFeeDueDays(), t.recurringFeeDueDays(), t.withdrawSuspendedAtTermEnd(), t.paysWhilePaused(), t.blocksEnrollment(), t.blocksExamRegistration(),
                t.blocksCertificates(), t.requireEnrollmentFeeForFinalExamOnlyStudents()));
    }

    public static void setEnrollmentFeeLeadMonths(ProgramYearBillingTemplate template, int value) {
        YearBillingTerms t = termsOf(template);
        replace(template, new YearBillingTerms(t.recurringFeeInvoicesCount(), t.enrollmentFeeCount(), t.firstEnrollmentFeeMonth(), t.secondEnrollmentFeeMonth(), value,
                t.lateEnrollmentFeeDueDays(), t.recurringFeeDueDays(), t.withdrawSuspendedAtTermEnd(), t.paysWhilePaused(), t.blocksEnrollment(), t.blocksExamRegistration(),
                t.blocksCertificates(), t.requireEnrollmentFeeForFinalExamOnlyStudents()));
    }

    public static void setLateEnrollmentFeeDueDays(ProgramYearBillingTemplate template, int value) {
        YearBillingTerms t = termsOf(template);
        replace(template, new YearBillingTerms(t.recurringFeeInvoicesCount(), t.enrollmentFeeCount(), t.firstEnrollmentFeeMonth(), t.secondEnrollmentFeeMonth(),
                t.enrollmentFeeLeadMonths(), value, t.recurringFeeDueDays(), t.withdrawSuspendedAtTermEnd(), t.paysWhilePaused(), t.blocksEnrollment(), t.blocksExamRegistration(),
                t.blocksCertificates(), t.requireEnrollmentFeeForFinalExamOnlyStudents()));
    }

    public static void setPaysWhilePaused(ProgramYearBillingTemplate template, boolean value) {
        YearBillingTerms t = termsOf(template);
        replace(template, new YearBillingTerms(t.recurringFeeInvoicesCount(), t.enrollmentFeeCount(), t.firstEnrollmentFeeMonth(), t.secondEnrollmentFeeMonth(),
                t.enrollmentFeeLeadMonths(), t.lateEnrollmentFeeDueDays(), t.recurringFeeDueDays(), t.withdrawSuspendedAtTermEnd(), value, t.blocksEnrollment(),
                t.blocksExamRegistration(), t.blocksCertificates(), t.requireEnrollmentFeeForFinalExamOnlyStudents()));
    }

    public static void setBlocksEnrollment(ProgramYearBillingTemplate template, boolean value) {
        YearBillingTerms t = termsOf(template);
        replace(template, new YearBillingTerms(t.recurringFeeInvoicesCount(), t.enrollmentFeeCount(), t.firstEnrollmentFeeMonth(), t.secondEnrollmentFeeMonth(),
                t.enrollmentFeeLeadMonths(), t.lateEnrollmentFeeDueDays(), t.recurringFeeDueDays(), t.withdrawSuspendedAtTermEnd(), t.paysWhilePaused(), value,
                t.blocksExamRegistration(), t.blocksCertificates(), t.requireEnrollmentFeeForFinalExamOnlyStudents()));
    }

    public static void setBlocksExamRegistration(ProgramYearBillingTemplate template, boolean value) {
        YearBillingTerms t = termsOf(template);
        replace(template, new YearBillingTerms(t.recurringFeeInvoicesCount(), t.enrollmentFeeCount(), t.firstEnrollmentFeeMonth(), t.secondEnrollmentFeeMonth(),
                t.enrollmentFeeLeadMonths(), t.lateEnrollmentFeeDueDays(), t.recurringFeeDueDays(), t.withdrawSuspendedAtTermEnd(), t.paysWhilePaused(), t.blocksEnrollment(),
                value, t.blocksCertificates(), t.requireEnrollmentFeeForFinalExamOnlyStudents()));
    }

    public static void setBlocksCertificates(ProgramYearBillingTemplate template, boolean value) {
        YearBillingTerms t = termsOf(template);
        replace(template, new YearBillingTerms(t.recurringFeeInvoicesCount(), t.enrollmentFeeCount(), t.firstEnrollmentFeeMonth(), t.secondEnrollmentFeeMonth(),
                t.enrollmentFeeLeadMonths(), t.lateEnrollmentFeeDueDays(), t.recurringFeeDueDays(), t.withdrawSuspendedAtTermEnd(), t.paysWhilePaused(), t.blocksEnrollment(),
                t.blocksExamRegistration(), value, t.requireEnrollmentFeeForFinalExamOnlyStudents()));
    }

    public static void setRequireEnrollmentFeeForFinalExamOnlyStudents(ProgramYearBillingTemplate template, boolean value) {
        YearBillingTerms t = termsOf(template);
        replace(template, new YearBillingTerms(t.recurringFeeInvoicesCount(), t.enrollmentFeeCount(), t.firstEnrollmentFeeMonth(), t.secondEnrollmentFeeMonth(),
                t.enrollmentFeeLeadMonths(), t.lateEnrollmentFeeDueDays(), t.recurringFeeDueDays(), t.withdrawSuspendedAtTermEnd(), t.paysWhilePaused(), t.blocksEnrollment(),
                t.blocksExamRegistration(), t.blocksCertificates(), value));
    }

    private static final LocalDate ALWAYS_EFFECTIVE = LocalDate.of(2000, 1, 1);

    public static void publish(EntityManager entityManager, ProgramYearBillingTemplate template) {
        ConfigurationTopic topic = ConfigurationTopic.ofYearTerms(template.programId(), template.getTerm().getId());
        entityManager.persist(ConfigurationVersion.publish(topic, termsOf(template), ALWAYS_EFFECTIVE, "Fixture de prueba", template.getCreatedBy().getId(), LocalDate.now(), null, Instant.now()));
        entityManager.flush();
    }

    public static void load(EntityManager entityManager, ProgramYearBillingTemplate template) {
        ConfigurationTopic topic = ConfigurationTopic.ofYearTerms(template.programId(), template.getTerm().getId());
        ConfigurationVersion latest = entityManager.createQuery("select v from ConfigurationVersion v where v.topicKey = :key order by v.id desc", ConfigurationVersion.class)
                .setParameter("key", topic.key()).setMaxResults(1).getSingleResult();
        replace(template, (YearBillingTerms) latest.values());
    }

    public static void publishCohortEntryDiscount(EntityManager entityManager, ProgramCohort cohort, Percentage discount) {
        entityManager.persist(ConfigurationVersion.publish(ConfigurationTopic.ofCohortEntry(cohort.getId()), new CohortEntryDiscount(discount), ALWAYS_EFFECTIVE, "Fixture de prueba",
                cohort.getCreatedBy().getId(), LocalDate.now(), null, Instant.now()));
        entityManager.flush();
    }

    public static void setRecurringFeeDueDays(ProgramYearBillingTemplate template, int value) {
        YearBillingTerms t = termsOf(template);
        replace(template, new YearBillingTerms(t.recurringFeeInvoicesCount(), t.enrollmentFeeCount(), t.firstEnrollmentFeeMonth(), t.secondEnrollmentFeeMonth(),
                t.enrollmentFeeLeadMonths(), t.lateEnrollmentFeeDueDays(), value, t.withdrawSuspendedAtTermEnd(), t.paysWhilePaused(), t.blocksEnrollment(),
                t.blocksExamRegistration(), t.blocksCertificates(), t.requireEnrollmentFeeForFinalExamOnlyStudents()));
    }

    public static void setWithdrawSuspendedAtTermEnd(ProgramYearBillingTemplate template, boolean value) {
        YearBillingTerms t = termsOf(template);
        replace(template, new YearBillingTerms(t.recurringFeeInvoicesCount(), t.enrollmentFeeCount(), t.firstEnrollmentFeeMonth(), t.secondEnrollmentFeeMonth(),
                t.enrollmentFeeLeadMonths(), t.lateEnrollmentFeeDueDays(), t.recurringFeeDueDays(), value, t.paysWhilePaused(), t.blocksEnrollment(),
                t.blocksExamRegistration(), t.blocksCertificates(), t.requireEnrollmentFeeForFinalExamOnlyStudents()));
    }

    public static ProgramYearBillingTemplate publish(com.red_escuela.backend.billing.configuration.repository.ConfigurationVersionRepository repository, ProgramYearBillingTemplate template) {
        ConfigurationTopic topic = ConfigurationTopic.ofYearTerms(template.programId(), template.getTerm().getId());
        repository.saveAndFlush(ConfigurationVersion.publish(topic, termsOf(template), ALWAYS_EFFECTIVE, "Fixture de prueba", template.getCreatedBy().getId(), LocalDate.now(), null, Instant.now()));
        return template;
    }

    private static volatile com.red_escuela.backend.billing.configuration.service.BillingConfigurationReader reader;

    static void useReader(com.red_escuela.backend.billing.configuration.service.BillingConfigurationReader configurationReader) {
        reader = configurationReader;
    }

    public static YearBillingTerms latest(ProgramYearBillingTemplate template) {
        return reader.requireYearTermsEffectiveOn(template.programId(), template.getTerm().getId(), LocalDate.of(2100, 1, 1));
    }
}
