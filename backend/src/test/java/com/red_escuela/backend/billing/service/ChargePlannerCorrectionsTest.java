package com.red_escuela.backend.billing.service;

import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.billing.dto.SelectPaymentPlanRequest;
import com.red_escuela.backend.billing.entity.CohortIntake;
import com.red_escuela.backend.billing.entity.EnrollmentBillingStanding;
import com.red_escuela.backend.billing.entity.FeeType;
import com.red_escuela.backend.billing.entity.Invoice;
import com.red_escuela.backend.billing.entity.InvoiceStatus;
import com.red_escuela.backend.billing.entity.PaymentPlanCadence;
import com.red_escuela.backend.billing.entity.PaymentPlanOption;
import com.red_escuela.backend.billing.entity.ProgramYearBillingTemplate;
import com.red_escuela.backend.billing.usecase.paymentplan.SelectPaymentPlanForProgramUseCase;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.enrollment.exception.EnrollmentFeeNotPaidForTermException;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.identity.entity.Person;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChargePlannerCorrectionsTest extends AbstractChargeSequenceTest {

    private static final YearMonth JAN_2026 = YearMonth.of(2026, 1);

    @Autowired
    private SelectPaymentPlanForProgramUseCase selectPaymentPlanUseCase;
    @Autowired
    private EnrollmentFeeRequirementService requirementService;

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    private void requireCertificate(Long enrollmentId) {
        transactionTemplate.executeWithoutResult(status -> requirementService.requireForCurrentPeriod(
                entityManager.find(ProgramEnrollment.class, enrollmentId), EnrollmentFeeRequirementService.Scope.CERTIFICATE));
    }

    private Year newUndatedYear(Career career, int year, int installments) {
        return transactionTemplate.execute(status -> {
            Term term = new Term();
            term.setYear(year);
            term.setDescription(String.valueOf(year));
            term.setStatus(StatusType.ACTIVE);
            entityManager.persist(term);
            ProgramYearBillingTemplate template = new ProgramYearBillingTemplate();
            template.setProgram(entityManager.getReference(Program.class, career.programId()));
            template.setTerm(term);
            template.setRecurringFeeInvoicesCount(installments);
            template.configureEnrollmentFees(1, 2, null);
            template.setCreatedBy(entityManager.getReference(Person.class, career.directorId()));
            entityManager.persist(template);
            return new Year(template.getId(), term.getId(), year);
        });
    }

    @Test
    @Tag("D-049")
    void lateEnrollmentChoosingThePlanThroughTheUseCaseKeepsTheMonthInstallment() {
        Career career = newCareer();
        Year y26 = newYear(career, 2026, 10, 1, 2, null);
        Long cohort = newCohort(y26, CohortIntake.FIRST, date(2026, 3, 1));
        today(date(2026, 4, 20));
        Long enrollment = newEnrollment(career, cohort, PaymentPlanCadence.MONTHLY, YearMonth.of(2026, 4), date(2026, 4, 20));
        Long optionId = transactionTemplate.execute(status -> {
            entityManager.createQuery("delete from PaymentPlanSelection s where s.programEnrollment.id = :id").setParameter("id", enrollment).executeUpdate();
            PaymentPlanOption option = new PaymentPlanOption();
            option.setProgram(entityManager.getReference(Program.class, career.programId()));
            option.setDescription("Mensual");
            option.setCadence(PaymentPlanCadence.MONTHLY);
            option.setCreatedBy(entityManager.getReference(Person.class, career.directorId()));
            entityManager.persist(option);
            return option.getId();
        });
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(career.directorId().toString(), null));

        selectPaymentPlanUseCase.select(enrollment, new SelectPaymentPlanRequest(optionId, null));

        assertThat(sequence(enrollment)).isEqualTo(expected("M2026-03", "C2026-04"));
        assertInvariants(enrollment, y26);
    }

    @Test
    @Tag("D-009")
    void movingTheSecondFeeMonthOfARunningYearNeverEmitsAThirdFee() {
        Career career = newCareer();
        Year y26 = newYear(career, 2026, 10, 2, 2, 7);
        Long cohort = newCohort(y26, CohortIntake.FIRST, date(2026, 3, 1));
        today(date(2026, 1, 5));
        Long enrollment = newEnrollment(career, cohort, PaymentPlanCadence.MONTHLY, JAN_2026, date(2026, 1, 5));
        issueEntryFee(enrollment);

        runEveryMonth(JAN_2026, YearMonth.of(2026, 7), () -> assertInvariants(enrollment, y26));
        assertThat(periods(enrollment, FeeType.ENROLLMENT_FEE)).containsExactly(YearMonth.of(2026, 3), YearMonth.of(2026, 7));

        configureYear(y26, template -> template.configureEnrollmentFees(2, 2, 8));
        runEveryMonth(YearMonth.of(2026, 8), YearMonth.of(2026, 12), () -> assertInvariants(enrollment, y26));

        assertThat(periods(enrollment, FeeType.ENROLLMENT_FEE)).hasSize(2);
        assertThat(invoices(enrollment, FeeType.ENROLLMENT_FEE)).extracting(Invoice::getEnrollmentFeeSlot).containsExactly(1, 2);
    }

    @Test
    void secondYearStudentOfUndatedTermsIsStillBilledAndTheCapCountsOnlyThatYear() {
        Career career = newCareer();
        Year y26 = newUndatedYear(career, 2026, 3);
        Year y27 = newUndatedYear(career, 2027, 3);
        Long cohort = newCohort(y26, CohortIntake.FIRST, date(2026, 3, 1));
        today(date(2026, 1, 5));
        Long enrollment = newEnrollment(career, cohort, PaymentPlanCadence.MONTHLY, JAN_2026, date(2026, 1, 5));
        issueEntryFee(enrollment);

        runEveryMonth(JAN_2026, YearMonth.of(2027, 12), () -> {
        });

        List<YearMonth> installments = periods(enrollment, FeeType.RECURRING_FEE);
        assertThat(installments.stream().filter(p -> p.getYear() == 2026)).hasSize(3);
        assertThat(installments.stream().filter(p -> p.getYear() == 2027)).hasSize(3);
        assertInvariants(enrollment, y26, y27);
    }

    @Test
    @Tag("D-015")
    void certificatesDoNotRequireTheFeeFromGraduatesNorEmitOneOnDemand() {
        Career career = newCareer();
        newYear(career, 2026, 10, 1, 2, null);
        today(date(2026, 3, 10));
        Long enrollment = newEnrollment(career, null, PaymentPlanCadence.MONTHLY, JAN_2026, date(2026, 1, 10));
        setStatus(enrollment, ProgramEnrollmentStatus.COMPLETED);

        requireCertificate(enrollment);

        assertThat(sequence(enrollment)).isEmpty();
    }

    @Test
    void certificatesNeverEmitOnDemandForSuspendedEnrollments() {
        Career career = newCareer();
        newYear(career, 2026, 10, 1, 2, null);
        today(date(2026, 3, 10));
        Long enrollment = newEnrollment(career, null, PaymentPlanCadence.MONTHLY, JAN_2026, date(2026, 1, 10));
        setStanding(enrollment, EnrollmentBillingStanding.SUSPENDED);

        requireCertificate(enrollment);

        assertThat(sequence(enrollment)).isEmpty();
    }

    @Test
    void aCancelledFeeBlocksTheGateAndIsNeverIssuedAgain() {
        Career career = newCareer();
        newYear(career, 2026, 10, 1, 2, null);
        today(date(2026, 3, 10));
        Long enrollment = newEnrollment(career, null, PaymentPlanCadence.MONTHLY, JAN_2026, date(2026, 1, 10));

        assertThatThrownBy(() -> requireCertificate(enrollment)).isInstanceOf(EnrollmentFeeNotPaidForTermException.class);
        assertThat(invoices(enrollment, FeeType.ENROLLMENT_FEE)).hasSize(1);

        markAllEnrollmentFees(enrollment, InvoiceStatus.CANCELLED);
        assertThatThrownBy(() -> requireCertificate(enrollment)).isInstanceOf(EnrollmentFeeNotPaidForTermException.class);
        job.run();

        assertThat(invoices(enrollment, FeeType.ENROLLMENT_FEE)).hasSize(1);
    }

    @Test
    @Tag("D-066")
    @Tag("contradicts-D-142")
    void suspendedEnrollmentsAreFrozenAndAreWithdrawnWithTheirDebtCancelledWhenTheYearEnds() {
        Career career = newCareer();
        Year y26 = newYear(career, 2026, 10, 1, 2, null);
        today(date(2026, 1, 5));
        Long enrollment = newEnrollment(career, null, PaymentPlanCadence.MONTHLY, JAN_2026, date(2026, 1, 5));
        runEveryMonth(JAN_2026, YearMonth.of(2026, 4), () -> {
        });
        setStanding(enrollment, EnrollmentBillingStanding.SUSPENDED);
        transactionTemplate.executeWithoutResult(status -> entityManager.find(ProgramEnrollment.class, enrollment)
                .setBillingStandingUpdatedAt(LocalDateTime.of(2026, 4, 20, 10, 0)));
        List<String> frozen = sequence(enrollment);

        runEveryMonth(YearMonth.of(2026, 5), YearMonth.of(2026, 12), () -> assertThat(sequence(enrollment)).isEqualTo(frozen));
        assertThat(entityManager.find(ProgramEnrollment.class, enrollment).getStatus()).isEqualTo(ProgramEnrollmentStatus.ENROLLED);

        today(date(2027, 1, 4));
        job.run();

        ProgramEnrollment closed = entityManager.find(ProgramEnrollment.class, enrollment);
        assertThat(closed.getStatus()).isEqualTo(ProgramEnrollmentStatus.WITHDRAWN);
        assertThat(closed.getBillingStanding()).isEqualTo(EnrollmentBillingStanding.WITHDRAWN_FOR_NONPAYMENT);
        assertThat(invoiceRepository.findByProgramEnrollmentIdOrderByPeriod(enrollment)).allMatch(i -> i.getStatus() == InvoiceStatus.WAIVED);
        assertThat(sequence(enrollment)).isEqualTo(frozen);
        assertInvariants(enrollment, y26);
    }

    @Test
    @Tag("D-068")
    void pausedEnrollmentsAreBilledOnlyWhenTheYearIsConfiguredToChargeWhilePaused() {
        Career career = newCareer();
        Year y26 = newYear(career, 2026, 10, 1, 2, null);
        today(date(2026, 1, 5));
        Long free = newEnrollment(career, null, PaymentPlanCadence.MONTHLY, JAN_2026, date(2026, 1, 5));
        Long paying = newEnrollment(career, null, PaymentPlanCadence.MONTHLY, JAN_2026, date(2026, 1, 5));
        setStatus(free, ProgramEnrollmentStatus.PAUSED);
        setStatus(paying, ProgramEnrollmentStatus.PAUSED);
        configureYear(y26, template -> template.setPaysWhilePaused(true));
        today(date(2026, 2, 1));
        job.run();
        today(date(2026, 3, 1));
        job.run();

        configureYear(y26, template -> template.setPaysWhilePaused(false));
        today(date(2026, 4, 1));
        job.run();

        assertThat(sequence(free)).isEqualTo(sequence(paying));
        assertThat(sequence(paying)).isEqualTo(expected("M2026-02", "C2026-03"));
    }

    @Test
    @Tag("D-040")
    void upfrontStudentWithoutANewChoiceFallsBackToMonthlyChargesInTheNextYear() {
        Career career = newCareer();
        Year y26 = newYear(career, 2026, 10, 1, 2, null);
        Year y27 = newYear(career, 2027, 10, 1, 2, null);
        Long cohort = newCohort(y26, CohortIntake.FIRST, date(2026, 3, 1));
        today(date(2026, 1, 5));
        Long enrollment = newEnrollment(career, cohort, PaymentPlanCadence.UPFRONT, JAN_2026, date(2026, 1, 5));
        issueEntryFee(enrollment);

        runEveryMonth(JAN_2026, YearMonth.of(2027, 12), () -> assertInvariants(enrollment, y26, y27));

        assertThat(periods(enrollment, FeeType.RECURRING_FEE).stream().filter(p -> p.getYear() == 2026)).hasSize(10);
        assertThat(periods(enrollment, FeeType.RECURRING_FEE).stream().filter(p -> p.getYear() == 2027)).hasSize(10);
        assertThat(periods(enrollment, FeeType.ENROLLMENT_FEE)).contains(YearMonth.of(2027, 2));
    }

    @Test
    @Tag("D-025")
    void aFeeEmittedAfterTheClosingOfTheWindowIsDueTenDaysLaterAndNeverBornOverdue() {
        Career career = newCareer();
        Year y26 = newYear(career, 2026, 10, 1, 2, null);
        today(date(2026, 3, 10));
        Long enrollment = newEnrollment(career, null, PaymentPlanCadence.MONTHLY, JAN_2026, date(2026, 1, 10));
        setStanding(enrollment, EnrollmentBillingStanding.SUSPENDED);
        today(date(2026, 5, 20));
        setStanding(enrollment, EnrollmentBillingStanding.UP_TO_DATE);
        transactionTemplate.executeWithoutResult(status -> eventPublisher().publishEvent(new com.red_escuela.backend.notification.SuspensionLiftedEvent(
                entityManager.find(ProgramEnrollment.class, enrollment), YearMonth.of(2026, 3), YearMonth.of(2026, 5))));

        Invoice fee = invoices(enrollment, FeeType.ENROLLMENT_FEE).get(0);
        assertThat(fee.getDueDate()).isEqualTo(date(2026, 5, 30));
        assertThat(fee.getEnrollmentFeeSlot()).isEqualTo(1);
        assertThat(fee.getTemplateId()).isEqualTo(y26.templateId());
    }

    @Autowired
    private org.springframework.context.ApplicationEventPublisher publisher;

    private org.springframework.context.ApplicationEventPublisher eventPublisher() {
        return publisher;
    }
}
