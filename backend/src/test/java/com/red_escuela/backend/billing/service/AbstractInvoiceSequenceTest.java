package com.red_escuela.backend.billing.service;

import com.red_escuela.backend.billing.support.BillingTestFixtures;
import com.red_escuela.backend.academicstructure.entity.CurricularTerm;
import com.red_escuela.backend.identity.entity.Institution;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.academicstructure.entity.ProgramSubject;
import com.red_escuela.backend.academicstructure.entity.Subject;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.billing.entity.CohortIntake;
import com.red_escuela.backend.billing.invariant.BillingInvariantChecker;
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
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.shared.time.MutableClock;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import com.red_escuela.backend.billing.planning.TestTemplateTerms;
import com.red_escuela.backend.shared.domain.Percentage;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.Clock;
import com.red_escuela.backend.billing.run.service.MonthlyInvoiceRunExecutor;
import com.red_escuela.backend.testsupport.MonthlyInvoiceRunTestDriver;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static com.red_escuela.backend.shared.domain.TestMoney.ars;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
abstract class AbstractInvoiceSequenceTest extends AbstractIntegrationTest {
    private static final AtomicLong DNI = new AtomicLong(700_000_000L + java.util.concurrent.ThreadLocalRandom.current().nextLong(0, 90_000_000L));

    protected MonthlyInvoiceRunTestDriver job;

    @Autowired
    void setMonthlyInvoiceRunTestDriver(MonthlyInvoiceRunExecutor executor, JdbcTemplate jdbc, Clock clock) {
        this.job = new MonthlyInvoiceRunTestDriver(executor, jdbc, clock);
    }
    @Autowired
    protected InvoiceRepository invoiceRepository;
    @Autowired
    protected EntityManager entityManager;
    @Autowired
    protected TransactionTemplate transactionTemplate;
    @Autowired
    protected BillingTestTrigger enrollmentBillingTrigger;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    protected BillingInvariantChecker invariantChecker;

    @BeforeEach
    void startInvariantChecker() {
        invariantChecker = BillingInvariantChecker.startingNow(jdbcTemplate);
    }

    protected record Year(Long templateId, Long termId, int year) {
    }

    protected record Career(Long programId, Long directorId, Long subjectId, Long institutionId) {
    }

    protected static LocalDate date(int year, int month, int day) {
        return LocalDate.of(year, month, day);
    }

    @Autowired
    private com.red_escuela.backend.billing.run.operation.TermEndWithdrawalBillingRunStep termEndWithdrawalStep;

    protected void closeFinishedTermsOfSuspendedEnrollments() {
        termEndWithdrawalStep.runStepForBusinessDate(LocalDate.now(MutableClock.INSTANCE));
    }

    protected void today(LocalDate date) {
        MutableClock.INSTANCE.setDate(date);
    }

    protected Person newPerson() {
        String dni = String.valueOf(DNI.incrementAndGet());
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

    protected Career newCareer() {
        return newCareer(LocalDate.of(2020, 1, 1));
    }

    protected Career newCareer(LocalDate enrollmentPriceFrom) {
        return transactionTemplate.execute(status -> {
            Person director = newPerson();
            Institution institution = new Institution();
            institution.setName("Instituto");
            entityManager.persist(institution);
            Program program = new Program();
            program.setName("Carrera " + System.nanoTime());
            program.setTotalYears(3);
            program.setTotalCreditsRequired(10);
            entityManager.persist(program);
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
            addPrice(program, director, FeeType.MONTHLY_INSTALLMENT, 10000, LocalDate.of(2020, 1, 1));
            addPrice(program, director, FeeType.ENROLLMENT_FEE, 5000, enrollmentPriceFrom);
            return new Career(program.getId(), director.getId(), subject.getId(), institution.getId());
        });
    }

    protected void addPrice(Program program, Person director, FeeType type, long amount, LocalDate from) {
        com.red_escuela.backend.billing.support.PriceFixtures.persistPriceVersion(program.getId(), null, type, "Precio", ars(amount), from, director.getId());
    }

    protected void newPrice(Career career, FeeType type, long amount, LocalDate from) {
        transactionTemplate.executeWithoutResult(status -> addPrice(entityManager.getReference(Program.class, career.programId()),
                entityManager.getReference(Person.class, career.directorId()), type, amount, from));
    }

    protected Year newYear(Career career, int year, int installments, int feeCount, int firstFeeMonth, Integer secondFeeMonth) {
        return transactionTemplate.execute(status -> {
            Term term = new Term();
            term.setYear(year);
            term.setDescription(String.valueOf(year));
            term.setStatus(StatusType.ACTIVE);
            term.setStartDate(LocalDateTime.of(year, 3, 1, 0, 0));
            term.setEndDate(LocalDateTime.of(year, 12, 31, 23, 59));
            entityManager.persist(term);
            ProgramYearBillingTemplate template = new ProgramYearBillingTemplate();
            template.setProgram(entityManager.getReference(Program.class, career.programId()));
            template.setTerm(term);
            com.red_escuela.backend.billing.planning.TestTemplateTerms.setRecurringFeeInvoicesCount(template, installments);
            com.red_escuela.backend.billing.planning.TestTemplateTerms.configureEnrollmentFees(template, feeCount, firstFeeMonth, secondFeeMonth);
            template.setCreatedBy(entityManager.getReference(Person.class, career.directorId()));
            entityManager.persist(template);
            com.red_escuela.backend.billing.planning.TestTemplateTerms.publish(entityManager, template);
            return new Year(template.getId(), term.getId(), year);
        });
    }

    protected void configureYear(Year year, java.util.function.Consumer<ProgramYearBillingTemplate> change) {
        transactionTemplate.executeWithoutResult(status -> {
            ProgramYearBillingTemplate template = entityManager.find(ProgramYearBillingTemplate.class, year.templateId());
            com.red_escuela.backend.billing.planning.TestTemplateTerms.load(entityManager, template);
            change.accept(template);
            com.red_escuela.backend.billing.planning.TestTemplateTerms.publish(entityManager, template);
        });
    }

    protected Long newCohort(Year year, CohortIntake intake, LocalDate cursadaStart) {
        return transactionTemplate.execute(status -> {
            Person director = newPerson();
            ProgramCohort cohort = new ProgramCohort();
            cohort.setTemplate(entityManager.getReference(ProgramYearBillingTemplate.class, year.templateId()));
            cohort.setIntake(intake);
            cohort.setAdmissionStart(cursadaStart.minusMonths(2));
            cohort.setAdmissionEnd(cursadaStart.minusDays(1));
            cohort.setCursadaStart(cursadaStart);
            cohort.setCreatedBy(director);
            entityManager.persist(cohort);
            if (intake == CohortIntake.SECOND) {
                TestTemplateTerms.publishCohortEntryDiscount(entityManager, cohort, Percentage.ofFraction(new java.math.BigDecimal("0.5")));
            }
            return cohort.getId();
        });
    }

    protected Long newEnrollment(Career career, Long cohortId, PaymentPlanCadence cadence, YearMonth planFrom, LocalDate enrolledOn) {
        return transactionTemplate.execute(status -> {
            Person person = newPerson();
            Student student = new Student();
            student.setPerson(person);
            student.setInstitution(entityManager.getReference(Institution.class, career.institutionId()));
            student.setOnboardingStatus(StudentOnboardingStatus.CONFIRMADO);
            entityManager.persist(student);
            Program program = entityManager.getReference(Program.class, career.programId());
            ProgramEnrollment enrollment = new ProgramEnrollment();
            enrollment.setStudent(student);
            enrollment.setProgram(program);
            if (cohortId != null) {
                enrollment.setProgramCohort(entityManager.getReference(ProgramCohort.class, cohortId));
            }
            enrollment.setStatus(ProgramEnrollmentStatus.ENROLLED);
            enrollment.setEnrollmentDate(enrolledOn.atTime(10, 0));
            entityManager.persist(enrollment);
            PaymentPlanOption option = new PaymentPlanOption();
            option.setProgram(program);
            option.setDescription("Plan");
            option.setCadence(cadence);
            option.setCreatedBy(entityManager.getReference(Person.class, career.directorId()));
            entityManager.persist(option);
            PaymentPlanSelection selection = new PaymentPlanSelection();
            selection.setProgramEnrollment(enrollment);
            selection.setPaymentPlanOption(option);
            selection.setEffectiveFrom(planFrom);
            selection.setScopeMonths(10);
            selection.setCreatedBy(entityManager.getReference(Person.class, career.directorId()));
            entityManager.persist(selection);
            return enrollment.getId();
        });
    }

    protected void issueEntryFee(Long enrollmentId) {
        transactionTemplate.executeWithoutResult(status -> {
            ProgramEnrollment enrollment = entityManager.find(ProgramEnrollment.class, enrollmentId);
            PaymentPlanSelection selection = entityManager.createQuery(
                    "select s from PaymentPlanSelection s where s.programEnrollment.id = :id", PaymentPlanSelection.class)
                    .setParameter("id", enrollmentId).getResultList().get(0);
            enrollmentBillingTrigger.issueEntryFee(enrollment, selection);
        });
    }

    protected void setStatus(Long enrollmentId, ProgramEnrollmentStatus statusValue) {
        transactionTemplate.executeWithoutResult(status -> entityManager.find(ProgramEnrollment.class, enrollmentId).setStatus(statusValue));
    }

    protected void setStanding(Long enrollmentId, EnrollmentPaymentStatus standing) {
        com.red_escuela.backend.billing.support.PaymentStandingSeed.seedProgramEnrollmentStanding(jdbcTemplate, enrollmentId, standing);
    }

    protected void markInvoices(Long enrollmentId, FeeType type, YearMonth period, InvoicePaymentStatus newStatus) {
        invoiceRepository.findByProgramEnrollmentIdOrderByPeriod(enrollmentId).stream()
                .filter(i -> i.getFeeType() == type && i.getPeriod().equals(period))
                .forEach(i -> applyPaymentStatus(i, newStatus));
    }

    protected void markAllEnrollmentFees(Long enrollmentId, InvoicePaymentStatus newStatus) {
        invoiceRepository.findByProgramEnrollmentIdOrderByPeriod(enrollmentId).stream()
                .filter(i -> i.getFeeType() == FeeType.ENROLLMENT_FEE)
                .forEach(i -> applyPaymentStatus(i, newStatus));
    }

    private void applyPaymentStatus(Invoice invoice, InvoicePaymentStatus newStatus) {
        switch (newStatus) {
            case PAID -> BillingTestFixtures.payInvoiceInFull(invoice);
            case OVERDUE -> BillingTestFixtures.markInvoiceAsOverdue(invoice);
            default -> throw new UnsupportedOperationException(newStatus.name());
        }
    }

    protected List<Invoice> invoices(Long enrollmentId, FeeType type) {
        return invoiceRepository.findByProgramEnrollmentIdOrderByPeriod(enrollmentId).stream().filter(i -> i.getFeeType() == type).toList();
    }

    protected List<YearMonth> periods(Long enrollmentId, FeeType type) {
        return invoices(enrollmentId, type).stream().map(Invoice::getPeriod).toList();
    }

    protected Term newCuatrimestre(int year, int startMonth, LocalDate windowOpens, LocalDate windowCloses, String type) {
        return transactionTemplate.execute(status -> {
            Term term = new Term();
            term.setYear(year);
            term.setDescription(year + "-" + type);
            term.setType(type);
            term.setStatus(StatusType.ACTIVE);
            term.setStartDate(LocalDateTime.of(year, startMonth, 1, 0, 0));
            term.setEndDate(LocalDateTime.of(year, startMonth + 4, 28, 23, 59));
            term.setEnrollmentStart(windowOpens.atStartOfDay());
            term.setEnrollmentEnd(windowCloses.atTime(23, 59));
            entityManager.persist(term);
            return term;
        });
    }

    protected Long newSection(Career career, Term term) {
        return transactionTemplate.execute(status -> {
            SubjectSection section = new SubjectSection();
            section.setName("Com " + System.nanoTime());
            section.setSubject(entityManager.getReference(Subject.class, career.subjectId()));
            section.setTerm(entityManager.getReference(Term.class, term.getId()));
            section.setStatus(StatusType.ACTIVE);
            entityManager.persist(section);
            return section.getId();
        });
    }

    protected void assertInvariants(Long enrollmentId, Year... years) {
        List<Invoice> all = invoiceRepository.findByProgramEnrollmentIdOrderByPeriod(enrollmentId);
        Set<String> seen = new HashSet<>();
        for (Invoice invoice : all) {
            assertThat(seen.add(invoice.getFeeType() + "@" + invoice.getPeriod()))
                    .as("sin dos cargos del mismo tipo y período: %s %s", invoice.getFeeType(), invoice.getPeriod()).isTrue();
            assertThat(invoice.getAmounts().linesTotal().value().currency()).isEqualTo(Currency.ARS);
        }
        for (Year year : years) {
            ProgramYearBillingTemplate template = transactionTemplate.execute(status -> {
                ProgramYearBillingTemplate loaded = entityManager.find(ProgramYearBillingTemplate.class, year.templateId());
                loaded.getTerm().getStartDate();
                return loaded;
            });
            long installments = all.stream().filter(i -> i.getFeeType() == FeeType.MONTHLY_INSTALLMENT)
                    .filter(i -> year.templateId().equals(i.getTemplateId())).count();
            assertThat(installments).as("cuotas del año lectivo %s", year.year()).isLessThanOrEqualTo(com.red_escuela.backend.billing.planning.TestTemplateTerms.latest(template).recurringFeeInvoicesCount());
            List<Invoice> fees = all.stream().filter(i -> i.getFeeType() == FeeType.ENROLLMENT_FEE)
                    .filter(i -> year.templateId().equals(i.getTemplateId())).toList();
            assertThat(fees).as("matrículas del ciclo %s", year.year()).hasSizeLessThanOrEqualTo(com.red_escuela.backend.billing.planning.TestTemplateTerms.latest(template).enrollmentFeeCount());
            assertThat(fees.stream().map(Invoice::getEnrollmentFeeSlot).filter(Objects::nonNull).distinct().count())
                    .as("una matrícula por número de slot en el ciclo %s", year.year()).isEqualTo(fees.stream().filter(f -> f.getEnrollmentFeeSlot() != null).count());
        }
    }

    protected com.red_escuela.backend.shared.domain.Money amountDue(Invoice invoice) {
        return transactionTemplate.execute(status -> invoiceRepository.findInvoiceWithLinesById(invoice.getId()).orElseThrow().getAmounts().total().value());
    }

    protected List<String> sequence(Long enrollmentId) {
        return invoiceRepository.findByProgramEnrollmentIdOrderByPeriod(enrollmentId).stream()
                .sorted(java.util.Comparator.comparing(Invoice::getPeriod).thenComparing(i -> i.getFeeType() == FeeType.ENROLLMENT_FEE ? 0 : 1))
                .map(i -> (i.getFeeType() == FeeType.ENROLLMENT_FEE ? "M" : "C") + i.getPeriod())
                .toList();
    }

    protected static List<String> expected(String... items) {
        List<String> result = new ArrayList<>();
        for (String item : items) {
            if (item.contains("..")) {
                String kind = item.substring(0, 1);
                String[] bounds = item.substring(1).split("\\.\\.");
                for (YearMonth month = YearMonth.parse(bounds[0]); !month.isAfter(YearMonth.parse(bounds[1])); month = month.plusMonths(1)) {
                    result.add(kind + month);
                }
            } else {
                result.add(item);
            }
        }
        return result;
    }

    protected void runEveryMonth(YearMonth from, YearMonth to, Runnable afterEachMonth) {
        for (YearMonth month = from; !month.isAfter(to); month = month.plusMonths(1)) {
            today(month.atDay(1));
            job.run();
            afterEachMonth.run();
            invariantChecker.assertAllBillingInvariantsHold();
        }
    }

    protected void addSectionEnrollment(Long enrollmentId, Career career, Term term, com.red_escuela.backend.enrollment.entity.EnrollmentStatus statusValue) {
        Long sectionId = newSection(career, term);
        transactionTemplate.executeWithoutResult(status -> {
            ProgramEnrollment enrollment = entityManager.find(ProgramEnrollment.class, enrollmentId);
            com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment sectionEnrollment = new com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment();
            sectionEnrollment.setStudent(enrollment.getStudent());
            sectionEnrollment.setProgramEnrollment(enrollment);
            sectionEnrollment.setSubjectSection(entityManager.getReference(SubjectSection.class, sectionId));
            sectionEnrollment.setStatus(statusValue);
            sectionEnrollment.setEnrolledAt(LocalDateTime.of(2026, 3, 1, 10, 0));
            sectionEnrollment.setAttemptNumber(1);
            entityManager.persist(sectionEnrollment);
        });
    }
}
