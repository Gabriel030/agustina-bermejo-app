package com.red_escuela.backend.billing.simulation;

import com.red_escuela.backend.academicstructure.domain.CurricularTerm;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.academicstructure.entity.ProgramSubject;
import com.red_escuela.backend.academicstructure.entity.Subject;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.billing.configuration.repository.ConfigurationVersionRepository;
import com.red_escuela.backend.billing.entity.CohortIntake;
import com.red_escuela.backend.billing.entity.PaymentPlanCadence;
import com.red_escuela.backend.billing.entity.PaymentPlanOption;
import com.red_escuela.backend.billing.entity.PaymentPlanSelection;
import com.red_escuela.backend.billing.entity.ProgramCohort;
import com.red_escuela.backend.billing.entity.ProgramYearBillingTemplate;
import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.billing.planning.TestTemplateTerms;
import com.red_escuela.backend.billing.service.BillingTestTrigger;
import com.red_escuela.backend.billing.service.LatePaymentTestFixtures;
import com.red_escuela.backend.billing.support.PriceFixtures;
import com.red_escuela.backend.config.TestSubjectCodes;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.identity.entity.Institution;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.Money;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.testsupport.TermFixtures;
import jakarta.persistence.EntityManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.concurrent.atomic.AtomicLong;

import static com.red_escuela.backend.shared.domain.TestMoney.ars;

public final class Fixtures {

    public static final int SCHOOL_YEAR = 2027;
    public static final Money MONTHLY_INSTALLMENT = ars(48000);
    public static final Money ENROLLMENT_FEE = ars(60000);
    public static final BigDecimal DAILY_LATE_FEE_FRACTION = new BigDecimal("0.0017");
    public static final int GRACE_DAYS = 5;
    public static final int DUE_DAYS = 10;
    public static final int INSTALLMENTS = 10;
    public static final LocalDate SCENARIO_START = LocalDate.of(SCHOOL_YEAR, 2, 15);
    public static final LocalDate FIRST_TERM_START = LocalDate.of(SCHOOL_YEAR, 3, 1);
    public static final LocalDate FIRST_TERM_END = LocalDate.of(SCHOOL_YEAR, 7, 31);
    public static final LocalDate SECOND_TERM_START = LocalDate.of(SCHOOL_YEAR, 8, 1);
    public static final LocalDate SECOND_TERM_END = LocalDate.of(SCHOOL_YEAR, 12, 31);

    private static final AtomicLong DNI = new AtomicLong(800_000_000L + java.util.concurrent.ThreadLocalRandom.current().nextLong(0, 90_000_000L));

    public record Catalog(Long programId, Long directorId, Long institutionId, Long templateId, Long cohortId) {
    }

    public record EnrolledStudent(Long studentId, Long personId, Long enrollmentId) {
    }

    private final EntityManager entityManager;
    private final TransactionTemplate transactions;
    private final ConfigurationVersionRepository configurations;
    private final JdbcTemplate jdbc;
    private final BillingTestTrigger billingTrigger;

    public Fixtures(EntityManager entityManager, TransactionTemplate transactions, ConfigurationVersionRepository configurations, JdbcTemplate jdbc,
                    BillingTestTrigger billingTrigger) {
        this.entityManager = entityManager;
        this.transactions = transactions;
        this.configurations = configurations;
        this.jdbc = jdbc;
        this.billingTrigger = billingTrigger;
    }

    public Catalog standardCatalog() {
        return transactions.execute(status -> {
            Person director = newPerson();
            Institution institution = new Institution();
            institution.setName("Instituto");
            entityManager.persist(institution);
            Program program = new Program();
            program.setVisibleInCatalog(true);
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
            PriceFixtures.persistPriceVersion(program.getId(), null, FeeType.MONTHLY_INSTALLMENT, "Cuota", MONTHLY_INSTALLMENT, LocalDate.of(2020, 1, 1), director.getId());
            PriceFixtures.persistPriceVersion(program.getId(), null, FeeType.ENROLLMENT_FEE, "Matricula", ENROLLMENT_FEE, LocalDate.of(2020, 1, 1), director.getId());
            Term term = TermFixtures.newTerm();
            term.setYear(SCHOOL_YEAR);
            term.setDescription(String.valueOf(SCHOOL_YEAR));
            term.setStatus(StatusType.ACTIVE);
            term.setStartDate(FIRST_TERM_START.atStartOfDay());
            term.setEndDate(SECOND_TERM_END.atTime(23, 59));
            entityManager.persist(term);
            ProgramYearBillingTemplate template = new ProgramYearBillingTemplate();
            template.setProgram(program);
            template.setTerm(term);
            TestTemplateTerms.setRecurringFeeInvoicesCount(template, INSTALLMENTS);
            TestTemplateTerms.configureEnrollmentFees(template, 1, 3, null);
            TestTemplateTerms.setRecurringFeeDueDays(template, DUE_DAYS);
            template.setCreatedBy(director);
            entityManager.persist(template);
            TestTemplateTerms.publish(entityManager, template);
            ProgramCohort cohort = new ProgramCohort();
            cohort.setTemplate(template);
            cohort.setIntake(CohortIntake.FIRST);
            cohort.setAdmissionStart(FIRST_TERM_START.minusMonths(2));
            cohort.setAdmissionEnd(FIRST_TERM_START.minusDays(1));
            cohort.setCursadaStart(FIRST_TERM_START);
            cohort.setCreatedBy(director);
            entityManager.persist(cohort);
            return new Catalog(program.getId(), director.getId(), institution.getId(), template.getId(), cohort.getId());
        });
    }

    public void configureLatePayment(Catalog catalog) {
        transactions.executeWithoutResult(status -> LatePaymentTestFixtures.persistLatePayment(configurations, entityManager.getReference(Person.class, catalog.directorId()),
                GRACE_DAYS, DAILY_LATE_FEE_FRACTION, 30, 2, 10, true, true, LocalDate.of(2000, 1, 1)));
        jdbc.update("""
                insert into configuration_version (scope, topic_key, config_values, status, effective_from, change_reason, proposed_by_id, proposed_at, decided_by_id, decided_at,
                                                   direct_publication)
                select 'ISSUANCE', 'ISSUANCE', '{"type":"ISSUANCE","issuanceMode":"DIRECT","continueIssuingWhileOverdue":true,"maxRetryAttempts":5,"retryBackoffHours":[1,3,6,12,24]}'::jsonb,
                       'EFFECTIVE', date '2000-01-01', 'Simulation fixture', ?, now(), ?, now(), true
                where not exists (select 1 from configuration_version where scope = 'ISSUANCE')
                """, catalog.directorId(), catalog.directorId());
    }

    public EnrolledStudent enrollStudent(Catalog catalog, LocalDate enrolledOn) {
        return transactions.execute(status -> {
            Person person = newPerson();
            Student student = new Student();
            student.setPerson(person);
            student.setInstitution(entityManager.getReference(Institution.class, catalog.institutionId()));
            student.setOnboardingStatus(StudentOnboardingStatus.CONFIRMADO);
            entityManager.persist(student);
            Program program = entityManager.getReference(Program.class, catalog.programId());
            ProgramEnrollment enrollment = new ProgramEnrollment();
            enrollment.setStudent(student);
            enrollment.setProgram(program);
            enrollment.setProgramCohort(entityManager.getReference(ProgramCohort.class, catalog.cohortId()));
            enrollment.setStatus(ProgramEnrollmentStatus.ENROLLED);
            enrollment.setEnrollmentDate(enrolledOn.atTime(10, 0));
            entityManager.persist(enrollment);
            PaymentPlanOption option = new PaymentPlanOption();
            option.setProgram(program);
            option.setDescription("Plan");
            option.setCadence(PaymentPlanCadence.MONTHLY);
            option.setCreatedBy(entityManager.getReference(Person.class, catalog.directorId()));
            entityManager.persist(option);
            PaymentPlanSelection selection = new PaymentPlanSelection();
            selection.setProgramEnrollment(enrollment);
            selection.setPaymentPlanOption(option);
            selection.setEffectiveFrom(YearMonth.from(enrolledOn));
            selection.setScopeMonths(INSTALLMENTS);
            selection.setCreatedBy(entityManager.getReference(Person.class, catalog.directorId()));
            entityManager.persist(selection);
            entityManager.flush();
            billingTrigger.issueInvoicesForNewPaymentPlan(enrollment, YearMonth.from(enrolledOn), enrolledOn);
            return new EnrolledStudent(student.getId(), person.getId(), enrollment.getId());
        });
    }

    private Person newPerson() {
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
}
