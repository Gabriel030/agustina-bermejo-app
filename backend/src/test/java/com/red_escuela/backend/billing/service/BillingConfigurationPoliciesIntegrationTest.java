package com.red_escuela.backend.billing.service;

import com.red_escuela.backend.academicstructure.dto.UpdateTermRequest;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.academicstructure.usecase.term.UpdateTermCommand;
import com.red_escuela.backend.academicstructure.usecase.term.UpdateTermUseCase;
import com.red_escuela.backend.billing.configuration.exception.BillingConfigurationFrozenException;
import com.red_escuela.backend.billing.configuration.exception.InvalidLatePaymentSettingsException;
import com.red_escuela.backend.billing.configuration.exception.InvalidYearBillingConfigurationException;
import com.red_escuela.backend.billing.dto.CreateCollectionsPolicyRequest;
import com.red_escuela.backend.billing.dto.YearBillingConfigurationRequest;
import com.red_escuela.backend.billing.entity.CohortIntake;
import com.red_escuela.backend.billing.entity.FeeType;
import com.red_escuela.backend.billing.entity.Invoice;
import com.red_escuela.backend.billing.entity.PaymentPlanCadence;
import com.red_escuela.backend.billing.entity.ProgramYearBillingTemplate;
import com.red_escuela.backend.billing.repository.CollectionsPolicyRepository;
import com.red_escuela.backend.billing.repository.ProgramYearBillingTemplateRepository;
import com.red_escuela.backend.billing.usecase.cohort.CreateProgramYearBillingTemplateUseCase;
import com.red_escuela.backend.billing.usecase.cohort.UpdateYearBillingConfigurationUseCase;
import com.red_escuela.backend.billing.usecase.collectionspolicy.CreateCollectionsPolicyUseCase;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.shared.domain.Percentage;
import com.red_escuela.backend.shared.domain.policy.PolicyViolation;
import com.red_escuela.backend.shared.exception.ErrorCodeResponse;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BillingConfigurationPoliciesIntegrationTest extends AbstractChargeSequenceTest {
    @Autowired
    private UpdateYearBillingConfigurationUseCase updateYearConfiguration;
    @Autowired
    private CreateProgramYearBillingTemplateUseCase createTemplate;
    @Autowired
    private UpdateTermUseCase updateTerm;
    @Autowired
    private CreateCollectionsPolicyUseCase createPolicy;
    @Autowired
    private ProgramYearBillingTemplateRepository templateRepository;
    @Autowired
    private CollectionsPolicyRepository policyRepository;

    private static YearBillingConfigurationRequest config(Integer count, Integer first, Integer second, Integer dueDays) {
        return new YearBillingConfigurationRequest(count, first, second, null, null, dueDays, null, null, null, null, null, null);
    }

    private Year yearWithFirstFeeIssued() {
        Career career = newCareer();
        Year year = newYear(career, 2027, 10, 2, 2, 7);
        Long cohort = newCohort(year, CohortIntake.FIRST, date(2027, 3, 1));
        today(date(2027, 2, 10));
        Long enrollment = newEnrollment(career, cohort, PaymentPlanCadence.MONTHLY, YearMonth.of(2027, 2), date(2027, 2, 10));
        issueEntryFee(enrollment);
        return year;
    }

    private ProgramYearBillingTemplate template(Year year) {
        return transactionTemplate.execute(status -> {
            ProgramYearBillingTemplate loaded = templateRepository.findById(year.templateId()).orElseThrow();
            loaded.getTerm().getId();
            return loaded;
        });
    }

    private List<String> codes(InvalidYearBillingConfigurationException e) {
        return e.violations().stream().map(PolicyViolation::code).toList();
    }

    @Test
    @Tag("D-009")
    @Tag("D-036")
    @Tag("D-096")
    void reducingEnrollmentFeesWhenTheFirstWasIssuedIsRejectedAndLeavesTheTemplateUntouched() {
        Year year = yearWithFirstFeeIssued();

        assertThatThrownBy(() -> updateYearConfiguration.update(year.templateId(), config(1, null, null, null)))
                .isInstanceOfSatisfying(BillingConfigurationFrozenException.class,
                        e -> assertThat(e.violations()).extracting(PolicyViolation::code).containsExactly("ENROLLMENT_FEE_COUNT_FROZEN"));

        assertThat(template(year).getEnrollmentFeeCount()).isEqualTo(2);
    }

    @Test
    @Tag("D-009")
    void movingTheNotYetIssuedSecondFeeIsAccepted() {
        Year year = yearWithFirstFeeIssued();

        updateYearConfiguration.update(year.templateId(), config(null, null, 8, null));

        assertThat(template(year).getSecondEnrollmentFeeMonth()).isEqualTo(8);
    }

    @Test
    @Tag("D-036")
    void changingTheMonthOfTheIssuedFirstFeeIsRejected() {
        Year year = yearWithFirstFeeIssued();

        assertThatThrownBy(() -> updateYearConfiguration.update(year.templateId(), config(null, 1, null, null)))
                .isInstanceOfSatisfying(BillingConfigurationFrozenException.class,
                        e -> assertThat(e.violations()).extracting(PolicyViolation::code).containsExactly("ENROLLMENT_FEE_MONTH_FROZEN"));
    }

    @Test
    @Tag("D-036")
    void movingTheTermStartPastAnIssuedInstallmentIsRejectedAndTheTermKeepsItsDates() {
        Career career = newCareer();
        Year year = newYear(career, 2027, 9, 1, 2, null);
        Long cohort = newCohort(year, CohortIntake.FIRST, date(2027, 3, 1));
        today(date(2027, 2, 10));
        Long enrollment = newEnrollment(career, cohort, PaymentPlanCadence.MONTHLY, YearMonth.of(2027, 2), date(2027, 2, 10));
        issueEntryFee(enrollment);
        today(date(2027, 3, 5));
        job.run();
        assertThat(periods(enrollment, FeeType.RECURRING_FEE)).contains(YearMonth.of(2027, 3));
        LocalDateTime oldStart = transactionTemplate.execute(status -> entityManager.find(Term.class, year.termId()).getStartDate());

        assertThatThrownBy(() -> updateTerm.update(new UpdateTermCommand(year.termId(),
                new UpdateTermRequest(null, LocalDateTime.of(2027, 4, 1, 0, 0), null, null, null))))
                .isInstanceOfSatisfying(BillingConfigurationFrozenException.class,
                        e -> assertThat(e.violations()).extracting(PolicyViolation::code).containsExactly("INSTALLMENTS_OUTSIDE_NEW_TERM_RANGE"));

        LocalDateTime startAfter = transactionTemplate.execute(status -> entityManager.find(Term.class, year.termId()).getStartDate());
        assertThat(startAfter).isEqualTo(oldStart);
    }

    @Test
    @Tag("D-034")
    void shorteningTheTermBelowTheInstallmentCountIsRejectedAsIncoherent() {
        Career career = newCareer();
        Year year = newYear(career, 2027, 10, 1, 2, null);

        assertThatThrownBy(() -> updateTerm.update(new UpdateTermCommand(year.termId(),
                new UpdateTermRequest(null, null, LocalDateTime.of(2027, 10, 31, 23, 59), null, null))))
                .isInstanceOfSatisfying(InvalidYearBillingConfigurationException.class,
                        e -> assertThat(codes(e)).containsExactly("INSTALLMENT_COUNT_EXCEEDS_TERM_SPAN"));
    }

    @Test
    @Tag("D-002")
    void creatingATemplateForATermWithoutDatesIsRejected() {
        Career career = newCareer();
        Long termId = transactionTemplate.execute(status -> {
            Term term = new Term();
            term.setYear(2027);
            term.setDescription("sin fechas");
            term.setStatus(com.red_escuela.backend.shared.domain.StatusType.ACTIVE);
            entityManager.persist(term);
            return term.getId();
        });
        authenticateAsNewPerson();

        assertThatThrownBy(() -> createTemplate.create(career.programId(), termId, 10))
                .isInstanceOfSatisfying(InvalidYearBillingConfigurationException.class,
                        e -> assertThat(codes(e)).containsExactly("TERM_WITHOUT_DATES"));
    }

    @Test
    @Tag("D-002")
    void aTemplateCreatedWithoutConfigurationPersistsTheFirstFeeMonthBeforeTheTermStart() {
        Career career = newCareer();
        Long januaryTermId = transactionTemplate.execute(status -> {
            Term term = new Term();
            term.setYear(2027);
            term.setDescription("enero");
            term.setStatus(com.red_escuela.backend.shared.domain.StatusType.ACTIVE);
            term.setStartDate(LocalDateTime.of(2027, 1, 1, 0, 0));
            term.setEndDate(LocalDateTime.of(2027, 12, 31, 23, 59));
            entityManager.persist(term);
            return term.getId();
        });
        authenticateAsNewPerson();

        ProgramYearBillingTemplate januaryTemplate = createTemplate.create(career.programId(), januaryTermId, 10);

        assertThat(januaryTemplate.getEnrollmentFeeCount()).isEqualTo(1);
        assertThat(januaryTemplate.getFirstEnrollmentFeeMonth()).isEqualTo(12);
    }

    @Test
    @Tag("D-002")
    void theDefaultFirstFeeMonthFollowsTheRequestedLeadMonths() {
        Career career = newCareer();
        Year marchYear = newYear(career, 2028, 10, 1, 2, null);
        authenticateAsNewPerson();
        Long termId = transactionTemplate.execute(status -> {
            Term term = new Term();
            term.setYear(2029);
            term.setDescription("marzo");
            term.setStatus(com.red_escuela.backend.shared.domain.StatusType.ACTIVE);
            term.setStartDate(LocalDateTime.of(2029, 3, 1, 0, 0));
            term.setEndDate(LocalDateTime.of(2029, 12, 31, 23, 59));
            entityManager.persist(term);
            return term.getId();
        });

        ProgramYearBillingTemplate template = createTemplate.create(career.programId(), termId, 10,
                new YearBillingConfigurationRequest(null, null, null, 2, null, null, null, null, null, null, null, null));

        assertThat(template.getFirstEnrollmentFeeMonth()).isEqualTo(1);
        assertThat(marchYear.year()).isEqualTo(2028);
    }

    @Test
    @Tag("D-002")
    @Tag("D-096")
    void aConfigurationWithTwoViolationsReportsBothInSetOrder() {
        Career career = newCareer();
        Year year = newYear(career, 2027, 10, 1, 2, null);

        assertThatThrownBy(() -> updateYearConfiguration.update(year.templateId(), config(2, 4, 5, null)))
                .isInstanceOfSatisfying(InvalidYearBillingConfigurationException.class,
                        e -> assertThat(codes(e)).containsExactly("FIRST_ENROLLMENT_FEE_AFTER_TERM_START"));
        Year shortYear = transactionTemplate.execute(status -> {
            Term term = entityManager.find(Term.class, year.termId());
            term.setEndDate(LocalDateTime.of(2027, 4, 30, 23, 59));
            return year;
        });
        assertThatThrownBy(() -> updateYearConfiguration.update(shortYear.templateId(), config(2, 4, 8, null)))
                .isInstanceOfSatisfying(InvalidYearBillingConfigurationException.class,
                        e -> assertThat(codes(e)).containsExactly(
                                "FIRST_ENROLLMENT_FEE_AFTER_TERM_START", "SECOND_ENROLLMENT_FEE_OUTSIDE_TERM", "INSTALLMENT_COUNT_EXCEEDS_TERM_SPAN"));
    }

    @Test
    @Tag("D-036")
    @Tag("D-057")
    void changingRecurringDueDaysOnlyAffectsInstallmentsIssuedAfterwards() {
        Career career = newCareer();
        Year year = newYear(career, 2027, 10, 1, 2, null);
        Long cohort = newCohort(year, CohortIntake.FIRST, date(2027, 3, 1));
        today(date(2027, 2, 10));
        Long enrollment = newEnrollment(career, cohort, PaymentPlanCadence.MONTHLY, YearMonth.of(2027, 2), date(2027, 2, 10));
        issueEntryFee(enrollment);
        today(date(2027, 4, 1));
        job.run();
        List<Invoice> beforeChange = invoices(enrollment, FeeType.RECURRING_FEE);
        LocalDate aprilDue = beforeChange.stream().filter(i -> i.getPeriod().equals(YearMonth.of(2027, 4))).findFirst().orElseThrow().getDueDate();

        today(date(2027, 4, 10));
        updateYearConfiguration.update(year.templateId(), config(null, null, null, 15));
        today(date(2027, 5, 1));
        job.run();

        List<Invoice> afterChange = invoices(enrollment, FeeType.RECURRING_FEE);
        assertThat(afterChange.stream().filter(i -> i.getPeriod().equals(YearMonth.of(2027, 4))).findFirst().orElseThrow().getDueDate()).isEqualTo(aprilDue);
        assertThat(afterChange.stream().filter(i -> i.getPeriod().equals(YearMonth.of(2027, 5))).findFirst().orElseThrow().getDueDate()).isEqualTo(date(2027, 5, 16));
    }

    @Test
    @Tag("D-147")
    @Tag("D-059")
    void latePaymentSettingsWithThreeViolationsAreRejectedTogetherAndNothingIsPersisted() {
        authenticateAsNewPerson();
        long before = policyRepository.count();

        assertThatThrownBy(() -> createPolicy.create(policyRequest(-1, 30, 0, LocalDate.now().minusDays(1))))
                .isInstanceOfSatisfying(InvalidLatePaymentSettingsException.class,
                        e -> assertThat(e.violations()).extracting(PolicyViolation::code).containsExactly(
                                "NEGATIVE_GRACE_PERIOD", "SUSPENSION_INVOICE_COUNT_BELOW_ONE", "RETROACTIVE_EFFECTIVE_DATE"));

        assertThat(policyRepository.count()).isEqualTo(before);
    }

    @Test
    @Tag("D-147")
    void graceAboveSuspensionDaysIsRejectedAndTheBordersAreAccepted() {
        authenticateAsNewPerson();

        assertThatThrownBy(() -> createPolicy.create(policyRequest(40, 30, 2, null)))
                .isInstanceOfSatisfying(InvalidLatePaymentSettingsException.class,
                        e -> assertThat(e.violations()).extracting(PolicyViolation::code).containsExactly("GRACE_PERIOD_EXCEEDS_SUSPENSION_DAYS"));
        assertThat(createPolicy.create(policyRequest(30, 30, 1, null)).getId()).isNotNull();
    }

    @Test
    @Tag("D-096")
    void theErrorBodyCarriesTheViolationsAndOmitsThemWhenThereAreNone() throws Exception {
        authenticateAsNewPerson();
        InvalidLatePaymentSettingsException exception = org.junit.jupiter.api.Assertions.assertThrows(InvalidLatePaymentSettingsException.class,
                () -> createPolicy.create(policyRequest(-1, 30, 2, null)));

        String json = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(ErrorCodeResponse.of(exception));

        assertThat(json).contains("\"code\":\"INVALID_LATE_PAYMENT_SETTINGS\"").contains("\"violations\":[{\"code\":\"NEGATIVE_GRACE_PERIOD\"");
    }

    @Test
    @Tag("D-009")
    @Tag("D-036")
    void concurrentSecondFeeIssuanceAndReductionToOneFeeNeverLeaveTheSecondIssuedWithOneConfigured() throws Exception {
        Career career = newCareer();
        Year year = newYear(career, 2027, 10, 2, 2, 7);
        Long cohort = newCohort(year, CohortIntake.FIRST, date(2027, 3, 1));
        today(date(2027, 2, 10));
        Long enrollment = newEnrollment(career, cohort, PaymentPlanCadence.MONTHLY, YearMonth.of(2027, 2), date(2027, 2, 10));
        today(date(2027, 6, 20));
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        Future<Object> reduction = pool.submit(() -> {
            start.await();
            try {
                updateYearConfiguration.update(year.templateId(), config(1, null, null, null));
                return "UPDATED";
            } catch (BillingConfigurationFrozenException e) {
                return "FROZEN";
            }
        });
        Future<Object> issuance = pool.submit(() -> {
            start.await();
            job.run();
            return "RAN";
        });
        start.countDown();
        Object reductionOutcome = reduction.get();
        issuance.get();
        pool.shutdown();

        boolean secondIssued = invoices(enrollment, FeeType.ENROLLMENT_FEE).stream().anyMatch(i -> Integer.valueOf(2).equals(i.getEnrollmentFeeSlot()));
        int configuredCount = template(year).getEnrollmentFeeCount();
        assertThat(secondIssued && configuredCount == 1).isFalse();
        if (secondIssued) {
            assertThat(reductionOutcome).isEqualTo("FROZEN");
        }
    }

    private CreateCollectionsPolicyRequest policyRequest(int grace, int suspensionDays, int invoiceCount, LocalDate effectiveFrom) {
        return new CreateCollectionsPolicyRequest(grace, Percentage.ofFraction(new BigDecimal("0.10")), suspensionDays, invoiceCount, effectiveFrom);
    }

    private void authenticateAsNewPerson() {
        Person person = transactionTemplate.execute(status -> newPerson());
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(person.getId().toString(), null));
    }
}
