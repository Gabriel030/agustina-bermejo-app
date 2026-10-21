package com.red_escuela.backend.billing.service;

import com.red_escuela.backend.billing.support.BillingTestFixtures;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.enrollment.dto.CreateProgramEnrollmentRequest;
import com.red_escuela.backend.shared.domain.Address;
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
import com.red_escuela.backend.billing.exception.StudentInDebtException;
import com.red_escuela.backend.identity.repository.AddressRepository;
import com.red_escuela.backend.academicstructure.repository.InstitutionRepository;
import com.red_escuela.backend.billing.invoice.repository.InvoiceRepository;
import com.red_escuela.backend.identity.repository.PersonRepository;
import com.red_escuela.backend.enrollment.repository.ProgramEnrollmentRepository;
import com.red_escuela.backend.academicstructure.repository.ProgramRepository;
import com.red_escuela.backend.identity.repository.StudentRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;

import static com.red_escuela.backend.billing.support.InvoiceAssertions.assertThatInvoice;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.red_escuela.backend.enrollment.service.ProgramEnrollmentService;
import static com.red_escuela.backend.shared.domain.TestMoney.ars;

@SpringBootTest
@ActiveProfiles("test")
class StudentDebtResolutionServiceTest extends AbstractIntegrationTest {
    @Autowired
    private StudentDebtResolutionService studentDebtResolutionService;

    @Autowired
    private ProgramEnrollmentService programEnrollmentService;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbc;

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

    @Autowired
    private com.red_escuela.backend.billing.discount.repository.StudentDiscountRepository studentDiscountRepository;
    @Autowired
    private com.red_escuela.backend.billing.discount.repository.DiscountDefinitionRepository discountDefinitionRepository;

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

    private Student persistStudent(String dni) {
        Institution institution = new Institution();
        institution.setName("Instituto de prueba " + System.nanoTime());
        institution = institutionRepository.save(institution);

        Student student = new Student();
        student.setPerson(persistPerson(dni));
        student.setInstitution(institution);
        student.setOnboardingStatus(StudentOnboardingStatus.CONFIRMADO);
        return studentRepository.save(student);
    }

    private Program persistProgram() {
        Program program = new Program();
        program.setName("Carrera de prueba " + System.nanoTime());
        program.setTotalYears(3);
        program.setTotalCreditsRequired(0);
        return programRepository.save(program);
    }

    private ProgramEnrollment persistOldWithdrawnCycle(Student student, Program program, BigDecimal deuda) {
        ProgramEnrollment enrollment = new ProgramEnrollment();
        enrollment.setStudent(student);
        enrollment.setProgram(program);
        enrollment.setStatus(ProgramEnrollmentStatus.WITHDRAWN);
        enrollment.setPaymentStatus(EnrollmentPaymentStatus.WITHDRAWN_FOR_NONPAYMENT);
        enrollment.setEnrollmentDate(LocalDateTime.now().minusMonths(6));
        enrollment = programEnrollmentRepository.save(enrollment);

        Invoice invoice = BillingTestFixtures.invoice().forProgramEnrollment(enrollment).ofType(FeeType.MONTHLY_INSTALLMENT).forPeriod(YearMonth.now().minusMonths(3)).dueOn(LocalDate.now().minusMonths(3)).withBaseAmount(ars(deuda)).build();
        BillingTestFixtures.markInvoiceAsOverdue(invoice);
        return enrollment;
    }

    @Test
    void aStudentWithoutDebtCanEnrollWithoutProblems() {
        Student student = persistStudent("88010001");
        Program program = persistProgram();

        var enrollment = programEnrollmentService.enroll(student.getId(), new CreateProgramEnrollmentRequest(program.getId()));

        assertThat(enrollment.getStatus()).isEqualTo(ProgramEnrollmentStatus.ENROLLED);
    }

    @Test
    @Tag("D-065")
    void studentWithOverdueDebtInOneProgramCannotEnrollInADifferentProgram() {
        Student student = persistStudent("88010002");
        Program programConDeuda = persistProgram();
        persistOldWithdrawnCycle(student, programConDeuda, BigDecimal.valueOf(5000));
        Program programNuevo = persistProgram();

        assertThatThrownBy(() -> programEnrollmentService.enroll(student.getId(), new CreateProgramEnrollmentRequest(programNuevo.getId())))
                .isInstanceOf(StudentInDebtException.class);
    }

    @Test
    void aPendingInvoiceWithinTheGracePeriodDoesNotBlockANewEnrollment() {
        Student student = persistStudent("88010003");
        Program programConCuotaPendiente = persistProgram();
        ProgramEnrollment enrollment = new ProgramEnrollment();
        enrollment.setStudent(student);
        enrollment.setProgram(programConCuotaPendiente);
        enrollment.setStatus(ProgramEnrollmentStatus.ENROLLED);
        enrollment.setEnrollmentDate(LocalDateTime.now());
        enrollment = programEnrollmentRepository.save(enrollment);
        Invoice invoice = BillingTestFixtures.invoice().forProgramEnrollment(enrollment).ofType(FeeType.MONTHLY_INSTALLMENT).forPeriod(YearMonth.now()).dueOn(LocalDate.now().plusDays(5)).withBaseAmount(ars(5000)).build();

        Program programNuevo = persistProgram();
        var nuevaMatricula = programEnrollmentService.enroll(student.getId(), new CreateProgramEnrollmentRequest(programNuevo.getId()));

        assertThat(nuevaMatricula.getStatus()).isEqualTo(ProgramEnrollmentStatus.ENROLLED);
    }

    @Test
    void payingInFullEnablesReEnrollment() {
        Person admin = persistPerson("admin.debt1." + System.nanoTime());
        Student student = persistStudent("88010004");
        Program program = persistProgram();
        ProgramEnrollment cicloViejo = persistOldWithdrawnCycle(student, program, BigDecimal.valueOf(5000));
        Invoice deudaVieja = invoiceRepository.findByProgramEnrollmentIdOrderByPeriod(cicloViejo.getId()).get(0);

        BillingTestFixtures.registerApprovedBankTransferPayment(deudaVieja.getId(), admin.getId());

        var nuevaMatricula = programEnrollmentService.enroll(student.getId(), new CreateProgramEnrollmentRequest(program.getId()));

        assertThat(nuevaMatricula.getStatus()).isEqualTo(ProgramEnrollmentStatus.ENROLLED);
    }

    @Test
    @Tag("D-136")
    void anAmountBelowTheInvoiceStaysAsCreditAndTheDebtStillBlocks() {
        Student student = persistStudent("88010005");
        Program program = persistProgram();
        ProgramEnrollment cicloViejo = persistOldWithdrawnCycle(student, program, BigDecimal.valueOf(5000));
        Invoice deudaVieja = invoiceRepository.findByProgramEnrollmentIdOrderByPeriod(cicloViejo.getId()).get(0);

        BillingTestFixtures.registerApprovedGatewayPayment(deudaVieja.getId(), ars(2000));

        assertThatThrownBy(() -> programEnrollmentService.enroll(student.getId(), new CreateProgramEnrollmentRequest(program.getId())))
                .isInstanceOf(StudentInDebtException.class);
        Invoice reloaded = invoiceRepository.findById(deudaVieja.getId()).orElseThrow();
        assertThat(reloaded.getPaymentStatus()).isNotEqualTo(InvoicePaymentStatus.PAID);
        assertThat(jdbc.queryForObject("select unallocated_credit_amount from student_current_account where id = ?", java.math.BigDecimal.class,
                reloaded.getStudentCurrentAccountId())).isEqualByComparingTo("2000");
    }

    @Test
    void theOldCyclesStudentDiscountDoesNotCarryOverAutomaticallyOnReenrollment() {
        Person director = persistPerson("director.debt6." + System.nanoTime());
        Student student = persistStudent("88010010");
        Program program = persistProgram();
        ProgramEnrollment cicloViejo = persistOldWithdrawnCycle(student, program, BigDecimal.valueOf(5000));

        Long definitionId = discountDefinitionRepository.findFirstByDiscountTypeAndStatusOrderByIdAsc(
                com.red_escuela.backend.billing.discount.domain.DiscountDefinitionType.SCHOLARSHIP, com.red_escuela.backend.billing.discount.domain.DiscountDefinitionStatus.ACTIVE).orElseThrow().getId();
        studentDiscountRepository.save(com.red_escuela.backend.billing.discount.domain.StudentDiscount.grantStudentDiscount(
                new com.red_escuela.backend.billing.discount.domain.StudentDiscountGrant(
                        com.red_escuela.backend.billing.discount.domain.EnrollmentReference.ofProgram(cicloViejo.getId()), student.getId(),
                        com.red_escuela.backend.shared.domain.Currency.ARS, definitionId, com.red_escuela.backend.billing.invoice.domain.DiscountType.SCHOLARSHIP,
                        com.red_escuela.backend.billing.invoice.domain.InvoiceType.MONTHLY_INSTALLMENT,
                        new com.red_escuela.backend.shared.domain.MonetaryAdjustment.Nominal(ars(2000)), null, 1,
                        new com.red_escuela.backend.billing.discount.domain.ScholarshipPeriodRange(java.time.YearMonth.of(2027, 3), java.time.YearMonth.of(2027, 5)),
                        "Beca del ciclo viejo"),
                director.getId(), null, java.time.Instant.parse("2027-02-01T10:00:00Z")));

        payInFull(director, invoiceRepository.findInvoiceWithLinesById(invoiceRepository.findByProgramEnrollmentIdOrderByPeriod(cicloViejo.getId()).get(0).getId()).orElseThrow());
        var nuevaMatricula = programEnrollmentService.enroll(student.getId(), new CreateProgramEnrollmentRequest(program.getId()));

        assertThat(studentDiscountRepository.findByProgramEnrollmentIdOrderByIdDesc(nuevaMatricula.getId())).isEmpty();
        assertThat(studentDiscountRepository.findByProgramEnrollmentIdOrderByIdDesc(cicloViejo.getId())).hasSize(1);
    }

    @Test
    void multipleOldCyclesWithDebtMustAllBeResolved() {
        Person director = persistPerson("director.debt4." + System.nanoTime());
        Student student = persistStudent("88010007");
        Program program = persistProgram();
        ProgramEnrollment ciclo1 = persistOldWithdrawnCycle(student, program, BigDecimal.valueOf(3000));
        ProgramEnrollment ciclo2 = persistOldWithdrawnCycle(student, program, BigDecimal.valueOf(4000));

        Invoice deuda1 = invoiceRepository.findInvoiceWithLinesById(
                invoiceRepository.findByProgramEnrollmentIdOrderByPeriod(ciclo1.getId()).get(0).getId()).orElseThrow();
        Invoice deuda2 = invoiceRepository.findInvoiceWithLinesById(
                invoiceRepository.findByProgramEnrollmentIdOrderByPeriod(ciclo2.getId()).get(0).getId()).orElseThrow();
        assertThatThrownBy(() -> programEnrollmentService.enroll(student.getId(), new CreateProgramEnrollmentRequest(program.getId())))
                .isInstanceOf(StudentInDebtException.class);
        payInFull(director, deuda1);
        assertThatThrownBy(() -> programEnrollmentService.enroll(student.getId(), new CreateProgramEnrollmentRequest(program.getId())))
                .isInstanceOf(StudentInDebtException.class);
        payInFull(director, deuda2);

        var nuevaMatricula = programEnrollmentService.enroll(student.getId(), new CreateProgramEnrollmentRequest(program.getId()));
        assertThat(nuevaMatricula.getStatus()).isEqualTo(ProgramEnrollmentStatus.ENROLLED);
    }

    @Test
    void payingOffDebtOnAnyEnrollmentUnblocksEnrollmentOnAnyOther() {
        Person admin = persistPerson("admin.debt5." + System.nanoTime());
        Student student = persistStudent("88010009");
        Program programConDeudaVieja = persistProgram();
        ProgramEnrollment cicloViejo = persistOldWithdrawnCycle(student, programConDeudaVieja, BigDecimal.valueOf(5000));
        Program programNuevo = persistProgram();

        assertThatThrownBy(() -> programEnrollmentService.enroll(student.getId(), new CreateProgramEnrollmentRequest(programNuevo.getId())))
                .isInstanceOf(StudentInDebtException.class);

        Invoice deudaVieja = invoiceRepository.findByProgramEnrollmentIdOrderByPeriod(cicloViejo.getId()).get(0);
        BillingTestFixtures.registerApprovedBankTransferPayment(deudaVieja.getId(), admin.getId());

        var nuevaMatricula = programEnrollmentService.enroll(student.getId(), new CreateProgramEnrollmentRequest(programNuevo.getId()));
        assertThat(nuevaMatricula.getStatus()).isEqualTo(ProgramEnrollmentStatus.ENROLLED);
    }

    @Test
    void directBypassOfEnrollStillBlockedWithOldDebt() {
        Student student = persistStudent("88010008");
        Program program = persistProgram();
        persistOldWithdrawnCycle(student, program, BigDecimal.valueOf(5000));

        assertThatThrownBy(() -> programEnrollmentService.enroll(student.getId(), new CreateProgramEnrollmentRequest(program.getId())))
                .isInstanceOf(StudentInDebtException.class);
    }

    private void payInFull(Person registrant, Invoice debt) {
        BillingTestFixtures.registerApprovedBankTransferPayment(debt.getId(), registrant.getId());
    }
}
