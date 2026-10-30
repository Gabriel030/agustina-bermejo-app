package com.red_escuela.backend.billing.service;

import com.red_escuela.backend.billing.support.BillingTestFixtures;
import com.red_escuela.backend.billing.support.PaymentStandingSeed;
import com.red_escuela.backend.billing.entity.EnrollmentPaymentStatus;
import com.red_escuela.backend.academicstructure.entity.Course;
import com.red_escuela.backend.academicstructure.entity.CurricularTerm;
import com.red_escuela.backend.identity.entity.Institution;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.academicstructure.entity.ProgramSubject;
import com.red_escuela.backend.academicstructure.entity.Subject;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.billing.entity.CohortIntake;
import com.red_escuela.backend.billing.entity.EnrollmentPaymentStatus;
import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.domain.InvoicePaymentStatus;
import com.red_escuela.backend.billing.entity.PaymentPlanCadence;
import com.red_escuela.backend.billing.entity.PaymentPlanOption;
import com.red_escuela.backend.billing.entity.PaymentPlanSelection;
import com.red_escuela.backend.billing.entity.ProgramCohort;
import com.red_escuela.backend.billing.entity.ProgramYearBillingTemplate;
import com.red_escuela.backend.billing.invoice.repository.InvoiceRepository;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.config.TestSubjectCodes;
import com.red_escuela.backend.enrollment.entity.CourseEnrollment;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.graduation.usecase.graduationrequest.ApproveGraduationRequestUseCase;
import com.red_escuela.backend.graduation.usecase.graduationrequest.RequestGraduationUseCase;
import com.red_escuela.backend.graduation.usecase.graduationrequest.VerifyGraduationRequestDebtUseCase;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.shared.domain.Grade;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import com.red_escuela.backend.billing.run.service.MonthlyInvoiceRunExecutor;
import com.red_escuela.backend.testsupport.MonthlyInvoiceRunTestDriver;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

import static com.red_escuela.backend.shared.domain.TestMoney.ars;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class MonthlyInvoiceRunIssuanceTest extends AbstractIntegrationTest {

    private static final YearMonth MARCH = YearMonth.of(2026, 3);

    private MonthlyInvoiceRunTestDriver job;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbc;

    @Autowired
    void setMonthlyInvoiceRunTestDriver(MonthlyInvoiceRunExecutor executor, org.springframework.jdbc.core.JdbcTemplate jdbc, java.time.Clock clock) {
        this.job = new MonthlyInvoiceRunTestDriver(executor, jdbc, clock);
    }

    @Autowired
    private InvoiceRepository invoiceRepository;


    @Autowired
    private RequestGraduationUseCase requestGraduationUseCase;

    @Autowired
    private VerifyGraduationRequestDebtUseCase verifyGraduationRequestDebtUseCase;

    @Autowired
    private ApproveGraduationRequestUseCase approveGraduationRequestUseCase;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private record Fixture(Person director, Student student, Subject subject, Term term, ProgramEnrollment enrollment) {
    }

    private Person persistPerson(String dni) {
        Address address = new Address();
        entityManager.persist(address);
        Person person = new Person();
        person.setName("Persona");
        person.setLastname(dni);
        person.setDni(dni);
        person.setUsername(dni);
        person.setEmail(dni + "@example.com");
        person.setAddress(address);
        person.setStatus(StatusType.ACTIVE);
        entityManager.persist(person);
        return person;
    }

    private Student persistStudent(String dni) {
        Institution institution = new Institution();
        institution.setName("Instituto de prueba");
        entityManager.persist(institution);
        Student student = new Student();
        student.setPerson(persistPerson(dni));
        student.setInstitution(institution);
        student.setOnboardingStatus(StudentOnboardingStatus.CONFIRMADO);
        entityManager.persist(student);
        return student;
    }

    private Term persistTerm() {
        Term term = new Term();
        term.setYear(2026);
        term.setDescription("2026");
        term.setStatus(StatusType.ACTIVE);
        term.setStartDate(LocalDateTime.of(2026, 3, 1, 0, 0));
        term.setEndDate(LocalDateTime.of(2026, 12, 31, 23, 59));
        entityManager.persist(term);
        return term;
    }

    private Subject persistSubject(Program program) {
        Subject subject = new Subject();
        subject.setCode(TestSubjectCodes.next());
        subject.setName("Materia " + System.nanoTime());
        entityManager.persist(subject);
        ProgramSubject programSubject = new ProgramSubject();
        programSubject.setProgram(program);
        programSubject.setSubject(subject);
        programSubject.setCredits(10);
        programSubject.setCurricularYear(1);
        programSubject.setCurricularTerm(CurricularTerm.FIRST);
        programSubject.setStatus(StatusType.ACTIVE);
        entityManager.persist(programSubject);
        return subject;
    }

    private Fixture setUpProgramEnrollment(String dni, PaymentPlanCadence cadence, int recurringCount, CohortIntake intake,
                                           YearMonth selectionFrom) {
        return transactionTemplate.execute(status -> {
            Person director = persistPerson(dni + "0");
            Student student = persistStudent(dni + "1");
            Program program = new Program();
            program.setName("Carrera " + System.nanoTime());
            program.setTotalYears(1);
            program.setTotalCreditsRequired(10);
            entityManager.persist(program);
            Subject subject = persistSubject(program);
            Term term = persistTerm();

            ProgramYearBillingTemplate template = new ProgramYearBillingTemplate();
            template.setProgram(program);
            template.setTerm(term);
            com.red_escuela.backend.billing.planning.TestTemplateTerms.setRecurringFeeInvoicesCount(template, recurringCount);
            template.setCreatedBy(director);
            entityManager.persist(template);
            com.red_escuela.backend.billing.planning.TestTemplateTerms.publish(entityManager, template);

            ProgramCohort cohort = new ProgramCohort();
            cohort.setTemplate(template);
            cohort.setIntake(intake);
            cohort.setAdmissionStart(LocalDate.of(2026, 1, 1));
            cohort.setAdmissionEnd(LocalDate.of(2026, 2, 28));
            cohort.setCursadaStart(LocalDate.of(2026, 3, 1));
            cohort.setCreatedBy(director);
            entityManager.persist(cohort);

            for (FeeType feeType : List.of(FeeType.MONTHLY_INSTALLMENT, FeeType.ENROLLMENT_FEE)) {
                com.red_escuela.backend.billing.support.PriceFixtures.persistPriceVersion(program.getId(), null, feeType, "Precio", ars(feeType == FeeType.MONTHLY_INSTALLMENT ? 10000 : 5000), LocalDate.of(2020, 1, 1), director.getId());
            }

            ProgramEnrollment enrollment = new ProgramEnrollment();
            enrollment.setStudent(student);
            enrollment.setProgram(program);
            enrollment.setProgramCohort(cohort);
            enrollment.setStatus(ProgramEnrollmentStatus.ENROLLED);
            enrollment.setEnrollmentDate(LocalDateTime.of(2026, 1, 15, 10, 0));
            entityManager.persist(enrollment);

            PaymentPlanOption option = new PaymentPlanOption();
            option.setProgram(program);
            option.setDescription("Plan");
            option.setCadence(cadence);
            option.setCreatedBy(director);
            entityManager.persist(option);

            PaymentPlanSelection selection = new PaymentPlanSelection();
            selection.setProgramEnrollment(enrollment);
            selection.setPaymentPlanOption(option);
            selection.setEffectiveFrom(selectionFrom);
            selection.setScopeMonths(10);
            selection.setCreatedBy(director);
            entityManager.persist(selection);
            entityManager.flush();
            return new Fixture(director, student, subject, term, enrollment);
        });
    }

    private Fixture monthlyFixture(String dni) {
        return setUpProgramEnrollment(dni, PaymentPlanCadence.MONTHLY, 10, CohortIntake.FIRST, YearMonth.of(2026, 1));
    }

    private void addSectionEnrollment(Fixture fixture, EnrollmentStatus status) {
        transactionTemplate.executeWithoutResult(tx -> {
            SubjectSection section = new SubjectSection();
            section.setName("Com A");
            section.setSubject(entityManager.getReference(Subject.class, fixture.subject().getId()));
            section.setTerm(entityManager.getReference(Term.class, fixture.term().getId()));
            section.setStatus(StatusType.ACTIVE);
            entityManager.persist(section);
            SubjectSectionEnrollment sectionEnrollment = new SubjectSectionEnrollment();
            sectionEnrollment.setStudent(entityManager.getReference(Student.class, fixture.student().getId()));
            sectionEnrollment.setProgramEnrollment(entityManager.getReference(ProgramEnrollment.class, fixture.enrollment().getId()));
            sectionEnrollment.setSubjectSection(section);
            sectionEnrollment.setStatus(status);
            if (status == EnrollmentStatus.APPROVED) {
                sectionEnrollment.setFinalScore(Grade.of(BigDecimal.valueOf(8.0)));
            }
            sectionEnrollment.setEnrolledAt(LocalDateTime.of(2026, 3, 1, 10, 0));
            sectionEnrollment.setAttemptNumber(1);
            entityManager.persist(sectionEnrollment);
        });
    }

    private void setPaymentStatus(Fixture fixture, EnrollmentPaymentStatus standing) {
        transactionTemplate.executeWithoutResult(tx ->
                entityManager.find(ProgramEnrollment.class, fixture.enrollment().getId()).setPaymentStatus(standing));
        PaymentStandingSeed.seedProgramEnrollmentStanding(jdbc, fixture.enrollment().getId(), standing);
    }

    private List<Invoice> invoicesOf(Fixture fixture, FeeType feeType) {
        return invoiceRepository.findByProgramEnrollmentIdOrderByPeriod(fixture.enrollment().getId()).stream()
                .filter(invoice -> invoice.getFeeType() == feeType)
                .toList();
    }

    private void setEnrollmentFeeMonth(Fixture fixture, int month) {
        transactionTemplate.executeWithoutResult(tx -> {
            var template = entityManager.find(ProgramEnrollment.class, fixture.enrollment().getId()).getProgramCohort().getTemplate();
            com.red_escuela.backend.billing.planning.TestTemplateTerms.load(entityManager, template);
            com.red_escuela.backend.billing.planning.TestTemplateTerms.configureEnrollmentFees(template, 1, month, null);
            com.red_escuela.backend.billing.planning.TestTemplateTerms.publish(entityManager, template);
        });
    }

    private void persistNextYearTemplate(Fixture fixture) {
        transactionTemplate.executeWithoutResult(tx -> {
            ProgramEnrollment enrollment = entityManager.find(ProgramEnrollment.class, fixture.enrollment().getId());
            Term nextTerm = new Term();
            nextTerm.setYear(2027);
            nextTerm.setDescription("2027");
            nextTerm.setStatus(StatusType.ACTIVE);
            nextTerm.setStartDate(LocalDateTime.of(2027, 3, 1, 0, 0));
            nextTerm.setEndDate(LocalDateTime.of(2027, 12, 31, 23, 59));
            entityManager.persist(nextTerm);
            ProgramYearBillingTemplate nextTemplate = new ProgramYearBillingTemplate();
            nextTemplate.setProgram(enrollment.getProgram());
            nextTemplate.setTerm(nextTerm);
            com.red_escuela.backend.billing.planning.TestTemplateTerms.setRecurringFeeInvoicesCount(nextTemplate, 10);
            nextTemplate.setCreatedBy(fixture.director());
            entityManager.persist(nextTemplate);
            com.red_escuela.backend.billing.planning.TestTemplateTerms.publish(entityManager, nextTemplate);
        });
    }

    private void setTotalCreditsRequired(Fixture fixture, int credits) {
        transactionTemplate.executeWithoutResult(tx ->
                entityManager.find(ProgramEnrollment.class, fixture.enrollment().getId()).getProgram().setTotalCreditsRequired(credits));
    }

    @Test
    void enrollmentMonthInsideTheTermIssuesTheEnrollmentFeeAndTheInstallment() {
        Fixture fixture = monthlyFixture("94100151");
        setEnrollmentFeeMonth(fixture, 3);

        job.run(MARCH);

        assertThat(invoicesOf(fixture, FeeType.ENROLLMENT_FEE)).extracting(Invoice::getPeriod).containsExactly(MARCH);
        assertThat(invoicesOf(fixture, FeeType.MONTHLY_INSTALLMENT)).extracting(Invoice::getPeriod).containsExactly(MARCH);
    }

    @Test
    void enrollmentMonthInsideTheTermStillIssuesTheInstallmentWhenTheEnrollmentFeeWasAlreadyIssuedThatYear() {
        Fixture fixture = monthlyFixture("94100161");
        setEnrollmentFeeMonth(fixture, 3);
        transactionTemplate.executeWithoutResult(tx -> {
            Invoice invoice = BillingTestFixtures.invoice().forProgramEnrollment(entityManager.getReference(ProgramEnrollment.class, fixture.enrollment().getId())).ofType(FeeType.ENROLLMENT_FEE).forPeriod(YearMonth.of(2026, 1)).dueOn(LocalDate.of(2026, 1, 1)).withBaseAmount(ars(5000)).build();
            BillingTestFixtures.payInvoiceInFull(invoice);
        });

        job.run(MARCH);

        assertThat(invoicesOf(fixture, FeeType.ENROLLMENT_FEE)).hasSize(1);
        assertThat(invoicesOf(fixture, FeeType.MONTHLY_INSTALLMENT)).extracting(Invoice::getPeriod).containsExactly(MARCH);
    }

    @Test
    void aNewStudentWithoutSubjectEnrollmentsIsNotConsideredToOweOnlyFinalExams() {
        Fixture fixture = monthlyFixture("94100171");
        setTotalCreditsRequired(fixture, 0);

        job.run(MARCH);

        assertThat(invoicesOf(fixture, FeeType.MONTHLY_INSTALLMENT)).extracting(Invoice::getPeriod).containsExactly(MARCH);
    }

    @Test
    void issuesTheMonthlyInstallmentToAnActiveProgramEnrollment() {
        Fixture fixture = monthlyFixture("94100001");

        job.run(MARCH);

        assertThat(invoicesOf(fixture, FeeType.MONTHLY_INSTALLMENT)).extracting(Invoice::getPeriod).containsExactly(MARCH);
    }

    @Test
    void runningTheJobTwiceInTheSameMonthDoesNotDuplicateInstallments() {
        Fixture fixture = monthlyFixture("94100011");

        job.run(MARCH);
        job.run(MARCH);

        assertThat(invoicesOf(fixture, FeeType.MONTHLY_INSTALLMENT)).hasSize(1);
    }

    @Test
    void doesNotIssueMoreInstallmentsThanTheTemplateDefines() {
        Fixture fixture = setUpProgramEnrollment("94100021", PaymentPlanCadence.MONTHLY, 1, CohortIntake.FIRST, YearMonth.of(2026, 1));

        job.run(MARCH);
        job.run(MARCH.plusMonths(1));

        assertThat(invoicesOf(fixture, FeeType.MONTHLY_INSTALLMENT)).extracting(Invoice::getPeriod).containsExactly(MARCH);
    }

    @Test
    @Tag("D-131")
    void anEnrollmentWithAnActiveAnnualUpfrontPlanIsNotIssuedAnythingByTheJob() {
        Fixture fixture = setUpProgramEnrollment("94100031", PaymentPlanCadence.ANNUAL_UPFRONT, 10, CohortIntake.FIRST, YearMonth.of(2026, 1));

        job.run(MARCH);
        job.run(MARCH.plusMonths(1));

        assertThat(invoicesOf(fixture, FeeType.MONTHLY_INSTALLMENT)).isEmpty();
    }

    @Test
    void doesNotIssueToWithdrawnForNonPaymentNorSuspendedEnrollments() {
        Fixture withdrawn = monthlyFixture("94100041");
        Fixture suspended = monthlyFixture("94100051");
        setPaymentStatus(withdrawn, EnrollmentPaymentStatus.WITHDRAWN_FOR_NONPAYMENT);
        setPaymentStatus(suspended, EnrollmentPaymentStatus.SUSPENDED);

        job.run(MARCH);

        assertThat(invoiceRepository.findByProgramEnrollmentIdOrderByPeriod(withdrawn.enrollment().getId())).isEmpty();
        assertThat(invoiceRepository.findByProgramEnrollmentIdOrderByPeriod(suspended.enrollment().getId())).isEmpty();
    }

    @Test
    void doesNotIssueToEnrollmentsThatAreNoLongerActive() {
        Fixture fixture = monthlyFixture("94100061");
        transactionTemplate.executeWithoutResult(tx ->
                entityManager.find(ProgramEnrollment.class, fixture.enrollment().getId()).setStatus(ProgramEnrollmentStatus.WITHDRAWN));

        job.run(MARCH);

        assertThat(invoiceRepository.findByProgramEnrollmentIdOrderByPeriod(fixture.enrollment().getId())).isEmpty();
    }

    @Test
    void doesNotIssueWhenThePlanIsCompleted() {
        Fixture fixture = monthlyFixture("94100071");
        addSectionEnrollment(fixture, EnrollmentStatus.APPROVED);

        job.run(MARCH);

        assertThat(invoiceRepository.findByProgramEnrollmentIdOrderByPeriod(fixture.enrollment().getId())).isEmpty();
    }

    @Test
    @Tag("D-037")
    void doesNotIssueWhenTheStudentOnlyOwesFinalExams() {
        Fixture fixture = monthlyFixture("94100081");
        addSectionEnrollment(fixture, EnrollmentStatus.REGULAR);

        job.run(MARCH);

        assertThat(invoiceRepository.findByProgramEnrollmentIdOrderByPeriod(fixture.enrollment().getId())).isEmpty();
    }

    @Test
    void keepsIssuingWhileTheStudentIsStillTakingASubject() {
        Fixture fixture = monthlyFixture("94100091");
        addSectionEnrollment(fixture, EnrollmentStatus.IN_PROGRESS);

        job.run(MARCH);

        assertThat(invoicesOf(fixture, FeeType.MONTHLY_INSTALLMENT)).hasSize(1);
    }

    @Test
    void issuesEnrollmentFeeOnlyInTheCohortEnrollmentFeeMonthAndRecurringFeeInTheRest() {
        Fixture fixture = monthlyFixture("94100101");

        job.run(YearMonth.of(2026, 2));
        job.run(YearMonth.of(2026, 2));
        job.run(MARCH);
        job.run(YearMonth.of(2026, 7));

        assertThat(invoicesOf(fixture, FeeType.ENROLLMENT_FEE)).extracting(Invoice::getPeriod).containsExactly(MARCH);
        assertThat(invoicesOf(fixture, FeeType.MONTHLY_INSTALLMENT)).extracting(Invoice::getPeriod)
                .containsExactly(MARCH, YearMonth.of(2026, 7));
    }

    @Test
    void secondIntakeStudentWhoEnteredInAugustPaysTheReenrollmentFeeInFebruaryAndNoneInJuly() {
        Fixture fixture = setUpProgramEnrollment("94100111", PaymentPlanCadence.MONTHLY, 10, CohortIntake.SECOND, YearMonth.of(2026, 1));
        transactionTemplate.executeWithoutResult(tx -> {
            Invoice entryFee = BillingTestFixtures.invoice().forProgramEnrollment(entityManager.getReference(ProgramEnrollment.class, fixture.enrollment().getId())).ofType(FeeType.ENROLLMENT_FEE).forPeriod(YearMonth.of(2026, 8)).dueOn(LocalDate.of(2026, 8, 1)).withBaseAmount(ars(2500)).build();
            BillingTestFixtures.payInvoiceInFull(entryFee);
        });

        persistNextYearTemplate(fixture);

        job.run(YearMonth.of(2026, 7));
        job.run(YearMonth.of(2027, 2));

        assertThat(invoicesOf(fixture, FeeType.ENROLLMENT_FEE)).extracting(Invoice::getPeriod)
                .containsExactly(YearMonth.of(2026, 8), YearMonth.of(2027, 2));
    }

    @Test
    void doesNotIssueAnotherEnrollmentFeeWhenOneWasAlreadyIssuedThatYear() {
        Fixture fixture = monthlyFixture("94100141");
        transactionTemplate.executeWithoutResult(tx -> {
            Invoice invoice = BillingTestFixtures.invoice().forProgramEnrollment(entityManager.getReference(ProgramEnrollment.class, fixture.enrollment().getId())).ofType(FeeType.ENROLLMENT_FEE).forPeriod(MARCH).dueOn(MARCH.atDay(1)).withBaseAmount(ars(5000)).build();
            BillingTestFixtures.payInvoiceInFull(invoice);
        });

        job.run(YearMonth.of(2026, 2));

        assertThat(invoicesOf(fixture, FeeType.ENROLLMENT_FEE)).hasSize(1);
    }

    @Test
    @Tag("D-039")
    void issuesOneInstallmentPerMonthToMonthlyCoursesAndNeverAnEnrollmentFee() {
        CourseEnrollment enrollment = transactionTemplate.execute(tx -> {
            Person director = persistPerson("94100121");
            Student student = persistStudent("94100122");
            Term term = new Term();
            term.setYear(2026);
            term.setStatus(StatusType.ACTIVE);
            entityManager.persist(term);
            Course course = new Course();
            course.setName("Curso " + System.nanoTime());
            course.setTerm(term);
            course.setCapacity(20);
            course.setIsActive(StatusType.ACTIVE);
            course.setDurationMonths(2);
            entityManager.persist(course);
            com.red_escuela.backend.billing.support.PriceFixtures.persistPriceVersion(null, course.getId(), FeeType.MONTHLY_INSTALLMENT, "Precio", ars(7000), LocalDate.of(2020, 1, 1), director.getId());
            CourseEnrollment courseEnrollment = new CourseEnrollment();
            courseEnrollment.setStudent(student);
            courseEnrollment.setCourse(course);
            courseEnrollment.setStatus(EnrollmentStatus.ENROLLED);
            courseEnrollment.setEnrollmentDate(LocalDateTime.of(2026, 2, 1, 10, 0));
            entityManager.persist(courseEnrollment);
            PaymentPlanOption option = new PaymentPlanOption();
            option.setCourse(course);
            option.setDescription("Mensual");
            option.setCadence(PaymentPlanCadence.MONTHLY);
            option.setCreatedBy(director);
            entityManager.persist(option);
            PaymentPlanSelection selection = new PaymentPlanSelection();
            selection.setCourseEnrollment(courseEnrollment);
            selection.setPaymentPlanOption(option);
            selection.setEffectiveFrom(YearMonth.of(2026, 2));
            selection.setCreatedBy(director);
            entityManager.persist(selection);
            entityManager.flush();
            return courseEnrollment;
        });

        job.run(YearMonth.of(2026, 2));
        job.run(YearMonth.of(2026, 2));
        job.run(MARCH);
        job.run(MARCH.plusMonths(1));

        List<Invoice> invoices = invoiceRepository.findByCourseEnrollmentIdOrderByPeriod(enrollment.getId());
        assertThat(invoices).extracting(Invoice::getFeeType).containsOnly(FeeType.MONTHLY_INSTALLMENT);
        assertThat(invoices).extracting(Invoice::getPeriod).containsExactly(YearMonth.of(2026, 2), MARCH);
    }

}
