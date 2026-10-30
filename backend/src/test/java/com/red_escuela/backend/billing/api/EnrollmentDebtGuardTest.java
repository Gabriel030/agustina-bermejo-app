package com.red_escuela.backend.billing.api;

import com.red_escuela.backend.billing.support.BillingTestFixtures;
import com.red_escuela.backend.shared.domain.Money;
import com.red_escuela.backend.enrollment.service.CourseEnrollmentService;
import com.red_escuela.backend.enrollment.dto.CreateCourseEnrollmentRequest;
import com.red_escuela.backend.shared.domain.policy.PolicyViolation;
import com.red_escuela.backend.enrollment.repository.CourseEnrollmentRepository;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.CourseEnrollment;
import com.red_escuela.backend.academicstructure.entity.Course;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.enrollment.dto.CreateProgramEnrollmentRequest;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.billing.entity.EnrollmentPaymentStatus;
import com.red_escuela.backend.billing.support.PaymentStandingSeed;
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
import com.red_escuela.backend.billing.invoice.exception.OutstandingDebtBlocksReenrollmentException;
import com.red_escuela.backend.identity.repository.AddressRepository;
import com.red_escuela.backend.identity.repository.InstitutionRepository;
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
    private CourseEnrollmentService courseEnrollmentService;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private org.springframework.transaction.PlatformTransactionManager transactionManager;

    @Autowired
    private CourseEnrollmentRepository courseEnrollmentRepository;

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
        enrollment.setEnrollmentDate(LocalDateTime.now().minusMonths(6));
        enrollment = programEnrollmentRepository.save(enrollment);
        PaymentStandingSeed.seedProgramEnrollmentStanding(jdbc, enrollment.getId(), EnrollmentPaymentStatus.WITHDRAWN_FOR_NONPAYMENT);

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
        Student student = persistStudent("88010005");
        Program program = persistProgram();
        ProgramEnrollment cicloViejo = persistOldWithdrawnCycle(student, program, BigDecimal.valueOf(5000));
        Invoice deudaVieja = invoiceRepository.findByProgramEnrollmentIdOrderByPeriod(cicloViejo.getId()).get(0);

        BillingTestFixtures.registerApprovedGatewayPayment(deudaVieja.getId(), ars(2000));

        assertThatThrownBy(() -> programEnrollmentService.enroll(student.getId(), new CreateProgramEnrollmentRequest(program.getId())))
                .isInstanceOf(OutstandingDebtBlocksReenrollmentException.class);
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

    private Invoice persistNotYetDueInvoiceOfWithdrawnCycle(ProgramEnrollment enrollment, Money amount) {
        return BillingTestFixtures.invoice().forProgramEnrollment(enrollment).ofType(FeeType.MONTHLY_INSTALLMENT).forPeriod(YearMonth.now().plusMonths(1))
                .dueOn(LocalDate.now().plusDays(20)).withBaseAmount(amount).build();
    }

    private ProgramEnrollment persistWithdrawnEnrollment(Student student, Program program) {
        ProgramEnrollment enrollment = new ProgramEnrollment();
        enrollment.setStudent(student);
        enrollment.setProgram(program);
        enrollment.setStatus(ProgramEnrollmentStatus.WITHDRAWN);
        enrollment.setEnrollmentDate(LocalDateTime.now().minusMonths(6));
        ProgramEnrollment saved = programEnrollmentRepository.save(enrollment);
        PaymentStandingSeed.seedProgramEnrollmentStanding(jdbc, saved.getId(), EnrollmentPaymentStatus.WITHDRAWN_FOR_NONPAYMENT);
        return saved;
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
        return ((OutstandingDebtBlocksReenrollmentException) thrown).violations().stream().map(PolicyViolation::code).toList();
    }

    @Test
    @Tag("C-16")
    @Tag("D-142")
    void aNotYetDueInvoiceOfAWithdrawnEnrollmentBlocksTheReturnUntilItIsPaid() {
        Person admin = persistPerson("admin.debt.c16." + System.nanoTime());
        Student student = persistStudent("88011001");
        Program program = persistProgram();
        Invoice debt = persistNotYetDueInvoiceOfWithdrawnCycle(persistWithdrawnEnrollment(student, program), ars(160000));

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
        Invoice debt = persistNotYetDueInvoiceOfWithdrawnCycle(persistWithdrawnEnrollment(student, program), ars(5000));
        jdbc.update("update invoice set payment_status = 'PAYMENT_UNDER_REVIEW' where id = ?", debt.getId());

        assertThat(violationCodesOf(student, program)).containsExactly("OUTSTANDING_DEBT_OF_WITHDRAWN_ENROLLMENT");
    }

    @Test
    @Tag("C-17")
    @Tag("D-142")
    void aCancelledInvoiceOfAWithdrawnEnrollmentNoLongerBlocksTheReturn() {
        Student student = persistStudent("88011003");
        Program program = persistProgram();
        Invoice debt = persistNotYetDueInvoiceOfWithdrawnCycle(persistWithdrawnEnrollment(student, program), ars(5000));
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
        Invoice debt = persistNotYetDueInvoiceOfWithdrawnCycle(persistWithdrawnEnrollment(student, program), ars(80000));
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
            Term term = new Term();
            term.setYear(2027);
            term.setDescription("2027-1C " + System.nanoTime());
            term.setStatus(StatusType.ACTIVE);
            entityManager.persist(term);
            Course course = new Course();
            course.setTerm(term);
            course.setName("Curso " + System.nanoTime());
            course.setIsActive(StatusType.ACTIVE);
            entityManager.persist(course);
            CourseEnrollment enrollment = new CourseEnrollment();
            enrollment.setStudent(student);
            enrollment.setCourse(course);
            enrollment.setStatus(EnrollmentStatus.WITHDRAWN);
            enrollment.setEnrollmentDate(LocalDateTime.now().minusMonths(2));
            return courseEnrollmentRepository.save(enrollment).getId();
        });
        CourseEnrollment withdrawnCourse = courseEnrollmentRepository.findById(courseEnrollmentId).orElseThrow();
        BillingTestFixtures.invoice().forCourseEnrollment(withdrawnCourse).ofType(FeeType.MONTHLY_INSTALLMENT).forPeriod(YearMonth.now().plusMonths(1))
                .dueOn(LocalDate.now().plusDays(20)).withBaseAmount(ars(5000)).build();

        assertThatThrownBy(() -> enrollmentDebtGuard.verifyNoOutstandingDebt(student.getId()))
                .isInstanceOf(OutstandingDebtBlocksReenrollmentException.class);
    }

    private ProgramEnrollment persistActiveEnrollment(Student student, Program program) {
        ProgramEnrollment enrollment = new ProgramEnrollment();
        enrollment.setStudent(student);
        enrollment.setProgram(program);
        enrollment.setStatus(ProgramEnrollmentStatus.ENROLLED);
        enrollment.setEnrollmentDate(LocalDateTime.now().minusMonths(2));
        return programEnrollmentRepository.save(enrollment);
    }

    @Test
    @Tag("C-16")
    @Tag("D-142")
    void aWithdrawnCourseDebtBlocksANewCourseEnrollmentThroughTheService() {
        Student student = persistStudent("88011006");
        Program program = persistProgram();
        persistNotYetDueInvoiceOfWithdrawnCycle(persistWithdrawnEnrollment(student, program), ars(5000));

        assertThatThrownBy(() -> courseEnrollmentService.enroll(student.getId(), new CreateCourseEnrollmentRequest(-1L, null)))
                .isInstanceOf(OutstandingDebtBlocksReenrollmentException.class);
    }

    @Test
    @Tag("D-142")
    void aDraftOrDiscardedInvoiceOfAWithdrawnEnrollmentDoesNotBlock() {
        Student student = persistStudent("88011007");
        Program program = persistProgram();
        Invoice invoice = persistNotYetDueInvoiceOfWithdrawnCycle(persistWithdrawnEnrollment(student, program), ars(5000));
        jdbc.update("update invoice set document_status = 'DISCARDED', discarded_reason = 'prueba', invoice_number = null, issue_date = null, due_date = null, student_snapshot = null, payment_status = null where id = ?", invoice.getId());

        assertThat(programEnrollmentService.enroll(student.getId(), new CreateProgramEnrollmentRequest(program.getId())).getStatus())
                .isEqualTo(ProgramEnrollmentStatus.ENROLLED);
    }

    @Test
    @Tag("D-065")
    void anOverdueInvoiceOfAnActiveEnrollmentBlocksAnyNewEnrollment() {
        Student student = persistStudent("88011008");
        Program activeProgram = persistProgram();
        Invoice invoice = BillingTestFixtures.invoice().forProgramEnrollment(persistActiveEnrollment(student, activeProgram)).ofType(FeeType.MONTHLY_INSTALLMENT)
                .forPeriod(YearMonth.now().minusMonths(2)).dueOn(LocalDate.now().minusMonths(2)).withBaseAmount(ars(5000)).build();
        BillingTestFixtures.markInvoiceAsOverdue(invoice);

        assertThat(violationCodesOf(student, persistProgram())).containsExactly("OVERDUE_INVOICE");
    }

    @Test
    @Tag("D-314")
    void aPendingRequestOnAnInvoiceOfAnActiveEnrollmentIsNotDebtForTheReturn() {
        Person requester = persistPerson("admin.debt.s12." + System.nanoTime());
        Student student = persistStudent("88011009");
        Program activeProgram = persistProgram();
        Invoice invoice = BillingTestFixtures.invoice().forProgramEnrollment(persistActiveEnrollment(student, activeProgram)).ofType(FeeType.MONTHLY_INSTALLMENT)
                .forPeriod(YearMonth.now().plusMonths(1)).dueOn(LocalDate.now().plusDays(20)).withBaseAmount(ars(5000)).build();
        persistPendingRequestOn(invoice, requester);

        assertThat(programEnrollmentService.enroll(student.getId(), new CreateProgramEnrollmentRequest(persistProgram().getId())).getStatus())
                .isEqualTo(ProgramEnrollmentStatus.ENROLLED);
    }

    private void payInFull(Person registrant, Invoice debt) {
        BillingTestFixtures.registerApprovedBankTransferPayment(debt.getId(), registrant.getId());
    }
}
