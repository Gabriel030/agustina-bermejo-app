package com.red_escuela.backend.billing.service;

import com.red_escuela.backend.billing.collections.domain.EnrollmentKind;
import com.red_escuela.backend.billing.collections.operation.EnrollmentPaymentStandingStep;
import com.red_escuela.backend.billing.configuration.domain.LatePaymentTerms;
import com.red_escuela.backend.billing.support.BillingTestFixtures;
import com.red_escuela.backend.shared.domain.Percentage;
import com.red_escuela.backend.shared.domain.Money;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.billing.configuration.repository.ConfigurationVersionRepository;
import com.red_escuela.backend.billing.configuration.domain.ConfigurationVersion;
import com.red_escuela.backend.billing.entity.EnrollmentPaymentStatus;
import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.identity.entity.Institution;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.domain.InvoicePaymentStatus;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.identity.repository.AddressRepository;
import com.red_escuela.backend.identity.repository.InstitutionRepository;
import com.red_escuela.backend.billing.invoice.repository.InvoiceRepository;
import com.red_escuela.backend.identity.repository.PersonRepository;
import com.red_escuela.backend.enrollment.repository.ProgramEnrollmentRepository;
import com.red_escuela.backend.academicstructure.repository.ProgramRepository;
import com.red_escuela.backend.identity.repository.StudentRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;

import static com.red_escuela.backend.billing.support.InvoiceAssertions.assertThatInvoice;
import static org.assertj.core.api.Assertions.assertThat;
import static com.red_escuela.backend.shared.domain.TestMoney.ars;

@SpringBootTest
@ActiveProfiles("test")
class EnrollmentMonthlyCutSuspensionTest extends AbstractIntegrationTest {
    @Autowired
    private EnrollmentPaymentStandingStep standingStep;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbc;

    @Autowired
    private ConfigurationVersionRepository configurationVersionRepository;

    @Autowired
    private AddressRepository addressRepository;

    @Autowired
    private PersonRepository personRepository;

    @Autowired
    private InstitutionRepository institutionRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private ProgramRepository programRepository;

    @Autowired
    private ProgramEnrollmentRepository programEnrollmentRepository;

    private Person persistPerson(String username) {
        Address address = addressRepository.save(new Address());
        Person person = new Person();
        person.setUsername(username);
        person.setDni(Long.toString(Math.abs((long) (username).hashCode())));
        person.setName("Persona");
        person.setLastname("Prueba");
        person.setEmail(username + "@example.com");
        person.setAddress(address);
        person.setStatus(StatusType.ACTIVE);
        return personRepository.save(person);
    }

    private ProgramEnrollment persistProgramEnrollment(String dni) {
        Institution institution = new Institution();
        institution.setName("Instituto de prueba " + System.nanoTime());
        institution = institutionRepository.save(institution);

        Student student = new Student();
        student.setPerson(persistPerson(dni));
        student.setInstitution(institution);
        student.setOnboardingStatus(StudentOnboardingStatus.CONFIRMADO);
        student = studentRepository.save(student);

        Program program = new Program();
        program.setName("Carrera de prueba " + System.nanoTime());
        program.setTotalYears(3);
        program.setTotalCreditsRequired(100);
        program = programRepository.save(program);

        ProgramEnrollment enrollment = new ProgramEnrollment();
        enrollment.setStudent(student);
        enrollment.setProgram(program);
        enrollment.setStatus(ProgramEnrollmentStatus.ENROLLED);
        enrollment.setEnrollmentDate(LocalDateTime.now());
        return programEnrollmentRepository.save(enrollment);
    }

    private Invoice persistInvoice(ProgramEnrollment enrollment, Money amount, LocalDate dueDate, InvoicePaymentStatus status) {
        return persistInvoice(enrollment, amount, dueDate, YearMonth.from(dueDate), status);
    }

    private Invoice persistInvoice(ProgramEnrollment enrollment, Money amount, LocalDate dueDate, YearMonth period, InvoicePaymentStatus status) {
        Invoice invoice = BillingTestFixtures.invoice().forProgramEnrollment(enrollment).ofType(FeeType.MONTHLY_INSTALLMENT).forPeriod(period).dueOn(dueDate).withBaseAmount(amount).inState(status).build();
        return invoice;
    }

    private ConfigurationVersion persistPolicy(Person createdBy, int suspensionAfterDays, int suspensionAfterInvoiceCount, int year) {
        return LatePaymentTestFixtures.persistLatePayment(configurationVersionRepository, createdBy, 10, BigDecimal.valueOf(0.10), suspensionAfterDays, suspensionAfterInvoiceCount,
                LocalDate.of(year, 1, 1));
    }

    private EnrollmentPaymentStatus statusOf(Long enrollmentId) {
        return EnrollmentPaymentStatus.valueOf(jdbc.queryForObject("select status from enrollment_payment_standing where program_enrollment_id = ?", String.class, enrollmentId));
    }

    private ProgramEnrollment reload(Long id) {
        return programEnrollmentRepository.findById(id).orElseThrow();
    }

    private void evaluateStanding(LocalDate businessDate, ProgramEnrollment... enrollments) {
        standingStep.evaluateEnrollmentsWithIds(EnrollmentKind.PROGRAM, java.util.Arrays.stream(enrollments).map(ProgramEnrollment::getId).toList(), businessDate, LatePaymentTerms.initial());
    }

    private static LocalDate d(int year, int month, int day) {
        return LocalDate.of(year, month, day);
    }

    @Test
    @Tag("D-241")
    void anInstallmentUnpaidAtMonthCloseSuspendsOnTheFirstOfTheNextMonth() {
        int year = 2040;
        persistPolicy(persistPerson("director.compliance1." + System.nanoTime()), 0, 1, year);
        ProgramEnrollment enrollment = persistProgramEnrollment("99000101");
        persistInvoice(enrollment, ars(1000), d(year, 5, 10), InvoicePaymentStatus.OVERDUE);

        evaluateStanding(d(year, 5, 31), enrollment);
        assertThat(statusOf(enrollment.getId())).isNotEqualTo(EnrollmentPaymentStatus.SUSPENDED);
        evaluateStanding(d(year, 6, 1), enrollment);

        assertThat(statusOf(enrollment.getId())).isEqualTo(EnrollmentPaymentStatus.SUSPENDED);
    }

    @Test
    @Tag("D-147")
    @Tag("D-175")
    @Tag("D-241")
    void aSuspendedStudentUnpaidForTwoMonthClosesStaysSuspendedAndEnrolled() {
        int year = 2041;
        persistPolicy(persistPerson("director.compliance2." + System.nanoTime()), 0, 1, year);
        ProgramEnrollment enrollment = persistProgramEnrollment("99000102");
        persistInvoice(enrollment, ars(1000), d(year, 4, 10), InvoicePaymentStatus.OVERDUE);

        evaluateStanding(d(year, 5, 1), enrollment);
        evaluateStanding(d(year, 6, 1), enrollment);

        assertThat(statusOf(enrollment.getId())).isEqualTo(EnrollmentPaymentStatus.SUSPENDED);
        assertThat(reload(enrollment.getId()).getStatus()).isEqualTo(ProgramEnrollmentStatus.ENROLLED);
    }

    @Test
    @Tag("D-147")
    @Tag("D-175")
    @Tag("D-241")
    void theNightlySuspensionWorksWithoutALoggedUserAndLeavesFutureInstallmentsPending() {
        int year = 2042;
        persistPolicy(persistPerson("director.compliance9." + System.nanoTime()), 0, 1, year);
        ProgramEnrollment enrollment = persistProgramEnrollment("99000120");
        persistInvoice(enrollment, ars(1000), d(year, 4, 10), InvoicePaymentStatus.OVERDUE);
        Invoice future = persistInvoice(enrollment, ars(1000), d(year, 7, 10), YearMonth.of(year, 7), InvoicePaymentStatus.PENDING);
        SecurityContextHolder.clearContext();

        evaluateStanding(d(year, 4, 20), enrollment);
        evaluateStanding(d(year, 5, 1), enrollment);

        assertThat(statusOf(enrollment.getId())).isEqualTo(EnrollmentPaymentStatus.SUSPENDED);
        assertThat(reload(enrollment.getId()).getStatus()).isEqualTo(ProgramEnrollmentStatus.ENROLLED);
        assertThatInvoice(invoiceRepository.findById(future.getId()).orElseThrow()).isPending();
    }

    @Test
    @Tag("D-241")
    void aSuspendedStudentWhoPaysTheDebtReturnsToGoodStandingWithoutBeingWithdrawn() {
        int year = 2043;
        persistPolicy(persistPerson("director.compliance3." + System.nanoTime()), 0, 1, year);
        ProgramEnrollment enrollment = persistProgramEnrollment("99000103");
        Invoice invoice = persistInvoice(enrollment, ars(1000), d(year, 4, 10), InvoicePaymentStatus.OVERDUE);
        evaluateStanding(d(year, 4, 20), enrollment);
        evaluateStanding(d(year, 5, 1), enrollment);
        assertThat(statusOf(enrollment.getId())).isEqualTo(EnrollmentPaymentStatus.SUSPENDED);

        BillingTestFixtures.payInvoiceInFull(invoice);
        evaluateStanding(d(year, 5, 2), enrollment);

        assertThat(statusOf(enrollment.getId())).isEqualTo(EnrollmentPaymentStatus.UP_TO_DATE);
        assertThat(reload(enrollment.getId()).getStatus()).isEqualTo(ProgramEnrollmentStatus.ENROLLED);
    }

    @Test
    @Tag("D-241")
    void oneUnpaidInstallmentDoesNotSuspendWhenThePolicyRequiresTwo() {
        int year = 2044;
        persistPolicy(persistPerson("director.compliance4." + System.nanoTime()), 0, 2, year);
        ProgramEnrollment enrollment = persistProgramEnrollment("99000104");
        persistInvoice(enrollment, ars(1000), d(year, 4, 10), InvoicePaymentStatus.OVERDUE);

        evaluateStanding(d(year, 5, 1), enrollment);

        assertThat(statusOf(enrollment.getId())).isNotEqualTo(EnrollmentPaymentStatus.SUSPENDED);
    }

    @Test
    @Tag("D-241")
    void twoUnpaidInstallmentsSuspendWhenThePolicyRequiresTwo() {
        int year = 2045;
        persistPolicy(persistPerson("director.compliance5." + System.nanoTime()), 0, 2, year);
        ProgramEnrollment enrollment = persistProgramEnrollment("99000105");
        persistInvoice(enrollment, ars(1000), d(year, 3, 10), YearMonth.of(year, 3), InvoicePaymentStatus.OVERDUE);
        persistInvoice(enrollment, ars(1000), d(year, 4, 10), YearMonth.of(year, 4), InvoicePaymentStatus.OVERDUE);

        evaluateStanding(d(year, 4, 20), enrollment);
        evaluateStanding(d(year, 5, 1), enrollment);

        assertThat(statusOf(enrollment.getId())).isEqualTo(EnrollmentPaymentStatus.SUSPENDED);
    }

    @Test
    @Tag("D-241")
    void theOldestInstallmentMustHaveTheConfiguredDaysOfDelayAtTheCutoff() {
        int year = 2046;
        persistPolicy(persistPerson("director.compliance10." + System.nanoTime()), 45, 1, year);
        ProgramEnrollment recent = persistProgramEnrollment("99000110");
        ProgramEnrollment old = persistProgramEnrollment("99000111");
        persistInvoice(recent, ars(1000), d(year, 4, 20), YearMonth.of(year, 4), InvoicePaymentStatus.OVERDUE);
        persistInvoice(old, ars(1000), d(year, 3, 1), YearMonth.of(year, 3), InvoicePaymentStatus.OVERDUE);

        evaluateStanding(d(year, 4, 20), recent, old);
        evaluateStanding(d(year, 5, 1), recent, old);

        assertThat(statusOf(recent.getId())).isNotEqualTo(EnrollmentPaymentStatus.SUSPENDED);
        assertThat(statusOf(old.getId())).isEqualTo(EnrollmentPaymentStatus.SUSPENDED);
    }

    @Test
    @Tag("D-241")
    void payingInFullOneOfTheTwoUnpaidInstallmentsThatCausedTheSuspensionDoesNotLiftTheSuspensionAlone() {
        int year = 2047;
        persistPolicy(persistPerson("director.compliance6." + System.nanoTime()), 0, 2, year);
        ProgramEnrollment enrollment = persistProgramEnrollment("99000106");
        Invoice first = persistInvoice(enrollment, ars(1000), d(year, 4, 10), YearMonth.of(year, 4), InvoicePaymentStatus.OVERDUE);
        persistInvoice(enrollment, ars(1000), d(year, 3, 10), YearMonth.of(year, 3), InvoicePaymentStatus.OVERDUE);
        evaluateStanding(d(year, 4, 20), enrollment);
        evaluateStanding(d(year, 5, 1), enrollment);
        assertThat(statusOf(enrollment.getId())).isEqualTo(EnrollmentPaymentStatus.SUSPENDED);

        BillingTestFixtures.payInvoiceInFull(first);
        evaluateStanding(d(year, 5, 2), enrollment);

        assertThat(statusOf(enrollment.getId())).isEqualTo(EnrollmentPaymentStatus.SUSPENDED);
    }

    @Test
    @Tag("D-241")
    void payingOffAllTheOverdueDebtLiftsTheSuspensionEvenIfThereIsAFutureInstallmentNotYetDue() {
        int year = 2048;
        persistPolicy(persistPerson("director.compliance12." + System.nanoTime()), 0, 1, year);
        ProgramEnrollment enrollment = persistProgramEnrollment("99000109");
        Invoice overdue = persistInvoice(enrollment, ars(1000), d(year, 4, 10), YearMonth.of(year, 4), InvoicePaymentStatus.OVERDUE);
        evaluateStanding(d(year, 4, 20), enrollment);
        evaluateStanding(d(year, 5, 1), enrollment);
        assertThat(statusOf(enrollment.getId())).isEqualTo(EnrollmentPaymentStatus.SUSPENDED);

        BillingTestFixtures.payInvoiceInFull(overdue);
        persistInvoice(enrollment, ars(1000), d(year, 6, 10), YearMonth.of(year, 6), InvoicePaymentStatus.PENDING);
        evaluateStanding(d(year, 5, 2), enrollment);

        assertThat(statusOf(enrollment.getId())).isEqualTo(EnrollmentPaymentStatus.UP_TO_DATE);
    }

    @Test
    @Tag("D-241")
    void aStudentWhoPaysEveryInstallmentStaysInGoodStandingAcrossSeveralMonthCloses() {
        int year = 2049;
        persistPolicy(persistPerson("director.compliance7." + System.nanoTime()), 0, 1, year);
        ProgramEnrollment enrollment = persistProgramEnrollment("99000107");
        for (int month = 1; month <= 4; month++) {
            persistInvoice(enrollment, ars(1000), d(year, month, 10), YearMonth.of(year, month), InvoicePaymentStatus.PAID);
        }
        evaluateStanding(d(year, 3, 1), enrollment);
        assertThat(statusOf(enrollment.getId())).isEqualTo(EnrollmentPaymentStatus.UP_TO_DATE);
        evaluateStanding(d(year, 4, 1), enrollment);
        assertThat(statusOf(enrollment.getId())).isEqualTo(EnrollmentPaymentStatus.UP_TO_DATE);

        Invoice pending = persistInvoice(enrollment, ars(1000), d(year, 5, 10), YearMonth.of(year, 5), InvoicePaymentStatus.PENDING);
        evaluateStanding(d(year, 5, 5), enrollment);
        assertThat(statusOf(enrollment.getId())).isEqualTo(EnrollmentPaymentStatus.UP_TO_DATE);

        BillingTestFixtures.payInvoiceInFull(pending);
        evaluateStanding(d(year, 6, 1), enrollment);
        assertThat(statusOf(enrollment.getId())).isEqualTo(EnrollmentPaymentStatus.UP_TO_DATE);
    }

    @Test
    @Tag("D-241")
    void theEvaluationIsIdempotentWithinTheSameBusinessDate() {
        int year = 2050;
        persistPolicy(persistPerson("director.compliance13." + System.nanoTime()), 0, 1, year);
        ProgramEnrollment enrollment = persistProgramEnrollment("99000113");
        persistInvoice(enrollment, ars(1000), d(year, 4, 10), InvoicePaymentStatus.OVERDUE);

        evaluateStanding(d(year, 4, 20), enrollment);
        evaluateStanding(d(year, 5, 1), enrollment);
        evaluateStanding(d(year, 5, 1), enrollment);

        assertThat(statusOf(enrollment.getId())).isEqualTo(EnrollmentPaymentStatus.SUSPENDED);
        assertThat(jdbc.queryForObject("""
                select count(*) from enrollment_payment_status_change c join enrollment_payment_standing s on s.id = c.standing_id
                where s.program_enrollment_id = ? and c.to_status = 'SUSPENDED'
                """, Long.class, enrollment.getId())).isEqualTo(1L);
    }
}
