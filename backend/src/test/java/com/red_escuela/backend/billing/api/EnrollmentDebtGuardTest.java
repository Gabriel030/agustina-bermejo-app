package com.red_escuela.backend.billing.api;

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
import com.red_escuela.backend.billing.invoice.exception.OutstandingDebtBlocksReenrollmentException;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;
import jakarta.persistence.EntityManager;

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
class EnrollmentDebtGuardTest extends AbstractIntegrationTest {
    @Autowired
    private EnrollmentDebtGuard enrollmentDebtGuard;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private org.springframework.transaction.PlatformTransactionManager transactionManager;

    @Autowired
    private com.red_escuela.backend.enrollment.repository.CourseEnrollmentRepository courseEnrollmentRepository;

    @Autowired
    private ProgramEnrollmentService programEnrollmentService;

    @Autowired
    private InvoiceRepository invoiceRepository;

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
    private com.red_escuela.backend.billing.repository.ScholarshipRepository scholarshipRepository;

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
                .isInstanceOf(OutstandingDebtBlocksReenrollmentException.class);
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
        Person admin = persistPerson("admin.debt2." + System.nanoTime());
        Student student = persistStudent("88010005");
        Program program = persistProgram();
        ProgramEnrollment cicloViejo = persistOldWithdrawnCycle(student, program, BigDecimal.valueOf(5000));
        Invoice deudaVieja = invoiceRepository.findByProgramEnrollmentIdOrderByPeriod(cicloViejo.getId()).get(0);

        BillingTestFixtures.registerApprovedGatewayPayment(deudaVieja.getId(), ars(2000));

        assertThatThrownBy(() -> programEnrollmentService.enroll(student.getId(), new CreateProgramEnrollmentRequest(program.getId())))
                .isInstanceOf(OutstandingDebtBlocksReenrollmentException.class);
    }

    @Test
    void theOldCyclesScholarshipDoesNotCarryOverAutomaticallyOnReenrollment() {
        Person director = persistPerson("director.debt6." + System.nanoTime());
        Student student = persistStudent("88010010");
        Program program = persistProgram();
        ProgramEnrollment cicloViejo = persistOldWithdrawnCycle(student, program, BigDecimal.valueOf(5000));

        com.red_escuela.backend.billing.entity.Scholarship scholarship = new com.red_escuela.backend.billing.entity.Scholarship();
        scholarship.setProgramEnrollment(cicloViejo);
        scholarship.setValue(new com.red_escuela.backend.shared.domain.MonetaryAdjustment.Nominal(ars(2000)));
        scholarship.setReason("Beca del ciclo viejo");
        scholarship.setGrantedBy(director);
        scholarship.addInstallments(3);
        scholarshipRepository.save(scholarship);

        payInFull(director, invoiceRepository.findInvoiceWithLinesById(invoiceRepository.findByProgramEnrollmentIdOrderByPeriod(cicloViejo.getId()).get(0).getId()).orElseThrow());
        var nuevaMatricula = programEnrollmentService.enroll(student.getId(), new CreateProgramEnrollmentRequest(program.getId()));

        assertThat(scholarshipRepository.findByProgramEnrollmentId(nuevaMatricula.getId())).isEmpty();
        assertThat(scholarshipRepository.findByProgramEnrollmentId(cicloViejo.getId())).hasSize(1);
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
                .isInstanceOf(OutstandingDebtBlocksReenrollmentException.class);
        payInFull(director, deuda1);
        assertThatThrownBy(() -> programEnrollmentService.enroll(student.getId(), new CreateProgramEnrollmentRequest(program.getId())))
                .isInstanceOf(OutstandingDebtBlocksReenrollmentException.class);
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
                .isInstanceOf(OutstandingDebtBlocksReenrollmentException.class);

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
                .isInstanceOf(OutstandingDebtBlocksReenrollmentException.class);
    }

    private Invoice persistNotYetDueInvoiceOfWithdrawnCycle(ProgramEnrollment enrollment, BigDecimal amount) {
        return BillingTestFixtures.invoice().forProgramEnrollment(enrollment).ofType(FeeType.MONTHLY_INSTALLMENT).forPeriod(YearMonth.now().plusMonths(1))
                .dueOn(LocalDate.now().plusDays(20)).withBaseAmount(ars(amount)).build();
    }

    private ProgramEnrollment persistWithdrawnEnrollment(Student student, Program program) {
        ProgramEnrollment enrollment = new ProgramEnrollment();
        enrollment.setStudent(student);
        enrollment.setProgram(program);
        enrollment.setStatus(ProgramEnrollmentStatus.WITHDRAWN);
        enrollment.setPaymentStatus(EnrollmentPaymentStatus.WITHDRAWN_FOR_NONPAYMENT);
        enrollment.setEnrollmentDate(LocalDateTime.now().minusMonths(6));
        return programEnrollmentRepository.save(enrollment);
    }

    private void markAsCancelled(Invoice invoice) {
        jdbc.update("update invoice set payment_status = 'CANCELLED', outstanding_amount = 0, credited_amount = total where id = ?", invoice.getId());
    }

    private Long persistPendingRequestOn(Invoice invoice, Person requester) {
        Long requestId = jdbc.queryForObject("""
                insert into authorization_request (operation_type, operation_payload, required_role, reason, status, requested_by_id, requested_at)
                values ('CANCEL_INVOICE', '{}'::jsonb, 'DIRECTOR', 'Baja', 'PENDING', ?, now()) returning id
                """, Long.class, requester.getId());
        jdbc.update("insert into authorization_request_invoice (authorization_request_id, invoice_id) values (?, ?)", requestId, invoice.getId());
        return requestId;
    }

    private java.util.List<String> violationCodesOf(Student student, Program program) {
        Throwable thrown = org.assertj.core.api.Assertions.catchThrowable(
                () -> programEnrollmentService.enroll(student.getId(), new CreateProgramEnrollmentRequest(program.getId())));
        assertThat(thrown).isInstanceOf(OutstandingDebtBlocksReenrollmentException.class);
        return ((OutstandingDebtBlocksReenrollmentException) thrown).violations().stream().map(com.red_escuela.backend.shared.domain.policy.PolicyViolation::code).toList();
    }

    @Test
    @Tag("C-16")
    @Tag("D-142")
    void aNotYetDueInvoiceOfAWithdrawnEnrollmentBlocksTheReturnUntilItIsPaid() {
        Person admin = persistPerson("admin.debt.c16." + System.nanoTime());
        Student student = persistStudent("88011001");
        Program program = persistProgram();
        Invoice debt = persistNotYetDueInvoiceOfWithdrawnCycle(persistWithdrawnEnrollment(student, program), BigDecimal.valueOf(160000));

        assertThat(violationCodesOf(student, program)).containsExactly("OUTSTANDING_DEBT_OF_WITHDRAWN_ENROLLMENT");
        BillingTestFixtures.registerApprovedBankTransferPayment(debt.getId(), admin.getId());

        assertThat(programEnrollmentService.enroll(student.getId(), new CreateProgramEnrollmentRequest(program.getId())).getStatus())
                .isEqualTo(ProgramEnrollmentStatus.ENROLLED);
    }

    @Test
    @Tag("C-17")
    @Tag("D-142")
    void aReceiptUnderReviewOfAWithdrawnEnrollmentKeepsBlockingTheReturn() {
        Student student = persistStudent("88011002");
        Program program = persistProgram();
        Invoice debt = persistNotYetDueInvoiceOfWithdrawnCycle(persistWithdrawnEnrollment(student, program), BigDecimal.valueOf(5000));
        jdbc.update("update invoice set payment_status = 'PAYMENT_UNDER_REVIEW' where id = ?", debt.getId());

        assertThat(violationCodesOf(student, program)).containsExactly("OUTSTANDING_DEBT_OF_WITHDRAWN_ENROLLMENT");
    }

    @Test
    @Tag("C-17")
    @Tag("D-142")
    void aCancelledInvoiceOfAWithdrawnEnrollmentNoLongerBlocksTheReturn() {
        Student student = persistStudent("88011003");
        Program program = persistProgram();
        Invoice debt = persistNotYetDueInvoiceOfWithdrawnCycle(persistWithdrawnEnrollment(student, program), BigDecimal.valueOf(5000));
        markAsCancelled(debt);

        assertThat(programEnrollmentService.enroll(student.getId(), new CreateProgramEnrollmentRequest(program.getId())).getStatus())
                .isEqualTo(ProgramEnrollmentStatus.ENROLLED);
    }

    @Test
    @Tag("D-314")
    @Tag("D-142")
    void anInvoiceWithAPendingCancellationRequestBlocksTheReturnUntilTheDirectorDecides() {
        Person requester = persistPerson("admin.debt.d314." + System.nanoTime());
        Student student = persistStudent("88011004");
        Program program = persistProgram();
        Invoice debt = persistNotYetDueInvoiceOfWithdrawnCycle(persistWithdrawnEnrollment(student, program), BigDecimal.valueOf(80000));
        Long requestId = persistPendingRequestOn(debt, requester);

        assertThat(violationCodesOf(student, program)).containsExactly("DEBT_IN_PENDING_CANCELLATION_REQUEST");

        jdbc.update("update authorization_request set status = 'REJECTED' where id = ?", requestId);
        assertThat(violationCodesOf(student, program)).containsExactly("OUTSTANDING_DEBT_OF_WITHDRAWN_ENROLLMENT");

        markAsCancelled(debt);
        assertThat(programEnrollmentService.enroll(student.getId(), new CreateProgramEnrollmentRequest(program.getId())).getStatus())
                .isEqualTo(ProgramEnrollmentStatus.ENROLLED);
    }

    @Test
    @Tag("C-16")
    @Tag("D-142")
    void aDebtOfAWithdrawnCourseEnrollmentBlocksTheGuardToo() {
        Student student = persistStudent("88011005");
        Long courseEnrollmentId = new TransactionTemplate(transactionManager).execute(status -> {
            com.red_escuela.backend.academicstructure.entity.Term term = new com.red_escuela.backend.academicstructure.entity.Term();
            term.setYear(2027);
            term.setDescription("2027-1C " + System.nanoTime());
            term.setStatus(StatusType.ACTIVE);
            entityManager.persist(term);
            com.red_escuela.backend.academicstructure.entity.Course course = new com.red_escuela.backend.academicstructure.entity.Course();
            course.setTerm(term);
            course.setName("Curso " + System.nanoTime());
            course.setIsActive(StatusType.ACTIVE);
            entityManager.persist(course);
            com.red_escuela.backend.enrollment.entity.CourseEnrollment enrollment = new com.red_escuela.backend.enrollment.entity.CourseEnrollment();
            enrollment.setStudent(student);
            enrollment.setCourse(course);
            enrollment.setStatus(com.red_escuela.backend.enrollment.entity.EnrollmentStatus.WITHDRAWN);
            enrollment.setEnrollmentDate(LocalDateTime.now().minusMonths(2));
            return courseEnrollmentRepository.save(enrollment).getId();
        });
        com.red_escuela.backend.enrollment.entity.CourseEnrollment withdrawnCourse = courseEnrollmentRepository.findById(courseEnrollmentId).orElseThrow();
        BillingTestFixtures.invoice().forCourseEnrollment(withdrawnCourse).ofType(FeeType.MONTHLY_INSTALLMENT).forPeriod(YearMonth.now().plusMonths(1))
                .dueOn(LocalDate.now().plusDays(20)).withBaseAmount(ars(BigDecimal.valueOf(5000))).build();

        assertThatThrownBy(() -> enrollmentDebtGuard.verifyNoOutstandingDebt(student.getId()))
                .isInstanceOf(OutstandingDebtBlocksReenrollmentException.class);
    }

    private void payInFull(Person registrant, Invoice debt) {
        BillingTestFixtures.registerApprovedBankTransferPayment(debt.getId(), registrant.getId());
    }
}
