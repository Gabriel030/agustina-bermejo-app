package com.red_escuela.backend.billing.service;

import com.red_escuela.backend.billing.support.BillingTestFixtures;
import com.red_escuela.backend.shared.domain.Percentage;
import com.red_escuela.backend.shared.domain.Money;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.billing.configuration.repository.ConfigurationVersionRepository;
import com.red_escuela.backend.billing.configuration.domain.ConfigurationVersion;
import com.red_escuela.backend.billing.entity.EnrollmentPaymentStatus;
import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.academicstructure.entity.Institution;
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
import com.red_escuela.backend.academicstructure.repository.InstitutionRepository;
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
class EnrollmentBillingComplianceSweepServiceTest extends AbstractIntegrationTest {
    @Autowired
    private EnrollmentBillingComplianceSweepService sweepService;

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

    private ConfigurationVersion persistPolicy(Person createdBy, int suspensionAfterDays, int suspensionAfterInvoiceCount) {
        return LatePaymentTestFixtures.persistLatePayment(configurationVersionRepository, createdBy, 10, BigDecimal.valueOf(0.10), suspensionAfterDays, suspensionAfterInvoiceCount,
                LocalDate.now());
    }

    private ProgramEnrollment reload(Long id) {
        return programEnrollmentRepository.findById(id).orElseThrow();
    }

    @Test
    void studentWithOneMonthOfNonPaymentIsSuspended() {
        Person director = persistPerson("director.compliance1." + System.nanoTime());
        persistPolicy(director, 30, 2);
        ProgramEnrollment enrollment = persistProgramEnrollment("99000101");
        persistInvoice(enrollment, ars(1000), LocalDate.now().minusDays(31), InvoicePaymentStatus.OVERDUE);
        jdbc.update("delete from collections_daily_snapshot where business_date = ?", LocalDate.now());

        sweepService.run(LocalDate.now());

        assertThat(reload(enrollment.getId()).getPaymentStatus()).isEqualTo(EnrollmentPaymentStatus.SUSPENDED);
        assertThat(jdbc.queryForMap("select overdue_31_60, students_with_overdue from collections_daily_snapshot where business_date = ? and currency = 'ARS' and program_id = ?",
                LocalDate.now(), enrollment.getProgram().getId())).containsEntry("overdue_31_60", new BigDecimal("1000.00")).containsEntry("students_with_overdue", 1);
    }

    @Test
    @Tag("D-147")
    @Tag("D-175")
    void suspendedStudentUnpaidForTwoMonthsStaysSuspendedAndEnrolled() {
        Person director = persistPerson("director.compliance2." + System.nanoTime());
        persistPolicy(director, 30, 2);
        ProgramEnrollment enrollment = persistProgramEnrollment("99000102");
        persistInvoice(enrollment, ars(1000), LocalDate.now().minusDays(61), InvoicePaymentStatus.OVERDUE);

        sweepService.run(LocalDate.now());

        ProgramEnrollment reloaded = reload(enrollment.getId());
        assertThat(reloaded.getPaymentStatus()).isEqualTo(EnrollmentPaymentStatus.SUSPENDED);
        assertThat(reloaded.getStatus()).isEqualTo(ProgramEnrollmentStatus.ENROLLED);
    }

    @Test
    @Tag("D-147")
    @Tag("D-175")
    void theNightlySuspensionWorksWithoutALoggedUserAndLeavesFutureInstallmentsPending() {
        Person director = persistPerson("director.compliance9." + System.nanoTime());
        persistPolicy(director, 30, 2);
        ProgramEnrollment enrollment = persistProgramEnrollment("99000120");
        persistInvoice(enrollment, ars(1000), LocalDate.now().minusDays(61), InvoicePaymentStatus.OVERDUE);
        Invoice future = persistInvoice(enrollment, ars(1000), LocalDate.now().plusDays(20),
                YearMonth.now().plusMonths(1), InvoicePaymentStatus.PENDING);
        SecurityContextHolder.clearContext();

        sweepService.run(LocalDate.now());

        ProgramEnrollment reloaded = reload(enrollment.getId());
        assertThat(reloaded.getPaymentStatus()).isEqualTo(EnrollmentPaymentStatus.SUSPENDED);
        assertThat(reloaded.getStatus()).isEqualTo(ProgramEnrollmentStatus.ENROLLED);
        assertThatInvoice(invoiceRepository.findById(future.getId()).orElseThrow()).isPending();
    }

    @Test
    void suspendedStudentWhoPaysBeforeTwoMonthsReturnsToGoodStandingWithoutBeingWithdrawn() {
        Person director = persistPerson("director.compliance3." + System.nanoTime());
        persistPolicy(director, 30, 2);
        ProgramEnrollment enrollment = persistProgramEnrollment("99000103");
        Invoice invoice = persistInvoice(enrollment, ars(1000), LocalDate.now().minusDays(31), InvoicePaymentStatus.OVERDUE);

        sweepService.run(LocalDate.now());
        assertThat(reload(enrollment.getId()).getPaymentStatus()).isEqualTo(EnrollmentPaymentStatus.SUSPENDED);

        BillingTestFixtures.payInvoiceInFull(invoice);
        sweepService.run(LocalDate.now());

        ProgramEnrollment reloaded = reload(enrollment.getId());
        assertThat(reloaded.getPaymentStatus()).isEqualTo(EnrollmentPaymentStatus.UP_TO_DATE);
        assertThat(reloaded.getStatus()).isEqualTo(ProgramEnrollmentStatus.ENROLLED);
    }

    @Test
    void twoSimultaneousOverdueInvoicesSuspendEvenIfNoneIsVeryOld() {
        Person director = persistPerson("director.compliance4." + System.nanoTime());
        persistPolicy(director, 30, 2);
        ProgramEnrollment enrollment = persistProgramEnrollment("99000104");
        persistInvoice(enrollment, ars(1000), LocalDate.now().minusDays(10), YearMonth.now(), InvoicePaymentStatus.OVERDUE);
        persistInvoice(enrollment, ars(1000), LocalDate.now().minusDays(15), YearMonth.now().minusMonths(1), InvoicePaymentStatus.OVERDUE);

        sweepService.run(LocalDate.now());

        assertThat(reload(enrollment.getId()).getPaymentStatus()).isEqualTo(EnrollmentPaymentStatus.SUSPENDED);
    }

    @Test
    void aSingleVeryOldInvoiceSuspendsEvenIfItIsTheOnlyOverdueOne() {
        Person director = persistPerson("director.compliance5." + System.nanoTime());
        persistPolicy(director, 30, 5);
        ProgramEnrollment enrollment = persistProgramEnrollment("99000105");
        persistInvoice(enrollment, ars(1000), LocalDate.now().minusDays(35), InvoicePaymentStatus.OVERDUE);

        sweepService.run(LocalDate.now());

        assertThat(reload(enrollment.getId()).getPaymentStatus()).isEqualTo(EnrollmentPaymentStatus.SUSPENDED);
    }

    @Test
    void payingInFullOneOfTheTwoOverdueInvoicesThatCausedTheSuspensionDoesNotLiftTheSuspensionAlone() {
        Person director = persistPerson("director.compliance6." + System.nanoTime());
        persistPolicy(director, 30, 2);
        ProgramEnrollment enrollment = persistProgramEnrollment("99000106");
        Invoice first = persistInvoice(enrollment, ars(1000), LocalDate.now().minusDays(10), YearMonth.now(), InvoicePaymentStatus.OVERDUE);
        persistInvoice(enrollment, ars(1000), LocalDate.now().minusDays(15), YearMonth.now().minusMonths(1), InvoicePaymentStatus.OVERDUE);
        sweepService.run(LocalDate.now());
        assertThat(reload(enrollment.getId()).getPaymentStatus()).isEqualTo(EnrollmentPaymentStatus.SUSPENDED);

        BillingTestFixtures.payInvoiceInFull(first);
        sweepService.run(LocalDate.now());

        assertThat(reload(enrollment.getId()).getPaymentStatus()).isEqualTo(EnrollmentPaymentStatus.SUSPENDED);
    }

    @Test
    void payingOffAllTheOverdueDebtLiftsTheSuspensionEvenIfThereIsAFutureInstallmentNotYetDue() {
        Person director = persistPerson("director.compliance9." + System.nanoTime());
        persistPolicy(director, 10, 2);
        ProgramEnrollment enrollment = persistProgramEnrollment("99000109");
        Invoice overdue = persistInvoice(enrollment, ars(1000), LocalDate.now().minusDays(15), YearMonth.now().minusMonths(1), InvoicePaymentStatus.OVERDUE);
        sweepService.run(LocalDate.now());
        assertThat(reload(enrollment.getId()).getPaymentStatus()).isEqualTo(EnrollmentPaymentStatus.SUSPENDED);

        BillingTestFixtures.payInvoiceInFull(overdue);
        persistInvoice(enrollment, ars(1000), LocalDate.now().plusDays(20), YearMonth.now().plusMonths(1), InvoicePaymentStatus.PENDING);
        sweepService.run(LocalDate.now());

        assertThat(reload(enrollment.getId()).getPaymentStatus()).isEqualTo(EnrollmentPaymentStatus.UP_TO_DATE);
    }

    @Test
    void studentWhoChangesPaymentPlanMidProgramStaysConsistentlyInGoodStanding() {
        Person director = persistPerson("director.compliance7." + System.nanoTime());
        persistPolicy(director, 10, 2);
        ProgramEnrollment enrollment = persistProgramEnrollment("99000107");

        YearMonth base = YearMonth.now().minusMonths(6);
        for (int i = 0; i < 5; i++) {
            persistInvoice(enrollment, ars(1000), base.plusMonths(i).atDay(1), InvoicePaymentStatus.PAID);
        }
        sweepService.run(LocalDate.now());
        assertThat(reload(enrollment.getId()).getPaymentStatus()).isEqualTo(EnrollmentPaymentStatus.UP_TO_DATE);

        Invoice monthlyInvoice = persistInvoice(enrollment, ars(1000), YearMonth.now().atDay(15), YearMonth.now(), InvoicePaymentStatus.PENDING);
        sweepService.run(LocalDate.now());
        assertThat(reload(enrollment.getId()).getPaymentStatus()).isEqualTo(EnrollmentPaymentStatus.UP_TO_DATE);

        BillingTestFixtures.payInvoiceInFull(monthlyInvoice);
        persistInvoice(enrollment, ars(1000), YearMonth.now().plusMonths(1).atDay(15), YearMonth.now().plusMonths(1), InvoicePaymentStatus.PENDING);
        sweepService.run(LocalDate.now().plusMonths(1));
        assertThat(reload(enrollment.getId()).getPaymentStatus()).isEqualTo(EnrollmentPaymentStatus.UP_TO_DATE);
    }

    @Test
    void paymentStatusStaysUpToDateWhenQueriedAtThreeDifferentMoments() {
        Person director = persistPerson("director.compliance8." + System.nanoTime());
        persistPolicy(director, 10, 2);
        ProgramEnrollment enrollment = persistProgramEnrollment("99000108");

        persistInvoice(enrollment, ars(1000), LocalDate.now().minusMonths(2), InvoicePaymentStatus.PAID);
        sweepService.run(LocalDate.now().minusMonths(2));
        assertThat(reload(enrollment.getId()).getPaymentStatus()).isEqualTo(EnrollmentPaymentStatus.UP_TO_DATE);

        persistInvoice(enrollment, ars(1000), LocalDate.now().minusMonths(1), InvoicePaymentStatus.PAID);
        sweepService.run(LocalDate.now().minusMonths(1));
        assertThat(reload(enrollment.getId()).getPaymentStatus()).isEqualTo(EnrollmentPaymentStatus.UP_TO_DATE);

        persistInvoice(enrollment, ars(1000), LocalDate.now().plusDays(5), InvoicePaymentStatus.PENDING);
        sweepService.run(LocalDate.now());
        assertThat(reload(enrollment.getId()).getPaymentStatus()).isEqualTo(EnrollmentPaymentStatus.UP_TO_DATE);
    }
}
