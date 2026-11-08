package com.red_escuela.backend.billing.service;

import com.red_escuela.backend.billing.dto.CreateAdHocInvoiceRequest;
import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.collections.exception.OverdueDebtBlocksExamFeeException;
import com.red_escuela.backend.billing.invoice.repository.InvoiceRepository;
import com.red_escuela.backend.billing.support.BillingTestFixtures;
import com.red_escuela.backend.billing.usecase.invoice.CreateAdHocInvoiceForProgramEnrollmentUseCase;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.academicstructure.repository.ProgramRepository;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.enrollment.repository.ProgramEnrollmentRepository;
import com.red_escuela.backend.identity.entity.Institution;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.identity.repository.AddressRepository;
import com.red_escuela.backend.identity.repository.InstitutionRepository;
import com.red_escuela.backend.identity.repository.PersonRepository;
import com.red_escuela.backend.identity.repository.StudentRepository;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.shared.domain.policy.PolicyViolation;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;

import static com.red_escuela.backend.shared.domain.TestMoney.ars;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

@Tag("D-152")
@Tag("D-292")
@SpringBootTest
@ActiveProfiles("test")
class ExamFeeOverdueDebtIT extends AbstractIntegrationTest {

    @Autowired
    private CreateAdHocInvoiceForProgramEnrollmentUseCase createAdHocInvoice;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbc;

    @Autowired
    private com.red_escuela.backend.billing.configuration.repository.ConfigurationVersionRepository configurationVersionRepository;

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
        person.setDni(Long.toString(Math.abs((long) username.hashCode())));
        person.setName("Persona");
        person.setLastname("Prueba");
        person.setEmail(username + "@example.com");
        person.setAddress(address);
        person.setStatus(StatusType.ACTIVE);
        return personRepository.save(person);
    }

    private ProgramEnrollment persistProgramEnrollment(String username) {
        Institution institution = new Institution();
        institution.setName("Instituto de prueba " + System.nanoTime());
        Student student = new Student();
        student.setPerson(persistPerson(username));
        student.setInstitution(institutionRepository.save(institution));
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

    private Invoice persistInvoice(ProgramEnrollment enrollment, FeeType feeType, long amount, LocalDate dueDate) {
        return BillingTestFixtures.invoice().forProgramEnrollment(enrollment).ofType(feeType).forPeriod(YearMonth.from(dueDate)).dueOn(dueDate)
                .withBaseAmount(ars(amount)).build();
    }

    private CreateAdHocInvoiceRequest examFeeRequest() {
        return new CreateAdHocInvoiceRequest(FeeType.EXAM_FEE, ars(1500), LocalDate.now(), "Derecho de examen final", null, null);
    }

    @Test
    void anOverdueInstallmentRejectsTheExamFeeWithItsCodeAndIssuesNothing() {
        ProgramEnrollment enrollment = persistProgramEnrollment("exam.fee.overdue." + System.nanoTime());
        persistInvoice(enrollment, FeeType.MONTHLY_INSTALLMENT, 5000, LocalDate.now().minusDays(5));

        Throwable thrown = catchThrowable(() -> createAdHocInvoice.create(enrollment.getId(), examFeeRequest()));

        assertThat(thrown).isInstanceOf(OverdueDebtBlocksExamFeeException.class);
        assertThat(((OverdueDebtBlocksExamFeeException) thrown).violations()).extracting(PolicyViolation::code).containsExactly("OVERDUE_DEBT_BLOCKS_EXAM_FEE");
        assertThat(invoiceRepository.findByProgramEnrollmentIdOrderByPeriod(enrollment.getId()))
                .noneMatch(invoice -> invoice.getFeeType() == FeeType.EXAM_FEE);
    }

    @Test
    void aLateFeeDebitNoteAloneBlocksTheExamFeeAfterTheInstallmentIsPaid() {
        ProgramEnrollment enrollment = persistProgramEnrollment("exam.fee.latefee." + System.nanoTime());
        Person admin = persistPerson("exam.fee.admin." + System.nanoTime());
        Invoice installment = persistInvoice(enrollment, FeeType.MONTHLY_INSTALLMENT, 5000, LocalDate.now().minusDays(20));
        Invoice lateFee = persistInvoice(enrollment, FeeType.ADMINISTRATIVE_FEE, 250, LocalDate.now().minusDays(4));
        jdbc.update("update invoice set document_type = 'DEBIT_NOTE', invoice_type = 'LATE_FEE', origin_invoice_id = ? where id = ?", installment.getId(), lateFee.getId());
        BillingTestFixtures.registerApprovedBankTransferPayment(installment.getId(), admin.getId());

        assertThat(catchThrowable(() -> createAdHocInvoice.create(enrollment.getId(), examFeeRequest()))).isInstanceOf(OverdueDebtBlocksExamFeeException.class);
    }

    @Test
    void anInstallmentDueTodayDoesNotBlockTheExamFee() {
        ProgramEnrollment enrollment = persistProgramEnrollment("exam.fee.today." + System.nanoTime());
        persistInvoice(enrollment, FeeType.MONTHLY_INSTALLMENT, 5000, LocalDate.now());

        Invoice exam = createAdHocInvoice.create(enrollment.getId(), examFeeRequest()).invoice();

        assertThat(exam.getFeeType()).isEqualTo(FeeType.EXAM_FEE);
    }

    @Test
    void payingTheOverdueDebtAllowsTheExamFee() {
        ProgramEnrollment enrollment = persistProgramEnrollment("exam.fee.paid." + System.nanoTime());
        Person admin = persistPerson("exam.fee.admin2." + System.nanoTime());
        Invoice overdue = persistInvoice(enrollment, FeeType.MONTHLY_INSTALLMENT, 5000, LocalDate.now().minusDays(5));
        assertThat(catchThrowable(() -> createAdHocInvoice.create(enrollment.getId(), examFeeRequest()))).isInstanceOf(OverdueDebtBlocksExamFeeException.class);

        BillingTestFixtures.registerApprovedBankTransferPayment(overdue.getId(), admin.getId());

        assertThat(createAdHocInvoice.create(enrollment.getId(), examFeeRequest()).invoice().getFeeType()).isEqualTo(FeeType.EXAM_FEE);
    }

    @Test
    void theOverdueDebtOfAnotherEnrollmentOfTheSameStudentAlsoBlocks() {
        ProgramEnrollment indebted = persistProgramEnrollment("exam.fee.other." + System.nanoTime());
        persistInvoice(indebted, FeeType.MONTHLY_INSTALLMENT, 5000, LocalDate.now().minusDays(5));
        Program otherProgram = new Program();
        otherProgram.setName("Otra carrera " + System.nanoTime());
        otherProgram.setTotalYears(3);
        otherProgram.setTotalCreditsRequired(100);
        ProgramEnrollment second = new ProgramEnrollment();
        second.setStudent(indebted.getStudent());
        second.setProgram(programRepository.save(otherProgram));
        second.setStatus(ProgramEnrollmentStatus.ENROLLED);
        second.setEnrollmentDate(LocalDateTime.now());
        ProgramEnrollment saved = programEnrollmentRepository.save(second);

        assertThat(catchThrowable(() -> createAdHocInvoice.create(saved.getId(), examFeeRequest()))).isInstanceOf(OverdueDebtBlocksExamFeeException.class);
    }

    @Test
    @Tag("D-288")
    void anInstallmentWithinTheGracePeriodDoesNotBlockTheExamFee() {
        ProgramEnrollment enrollment = persistProgramEnrollment("exam.fee.grace." + System.nanoTime());
        LatePaymentTestFixtures.persistLatePayment(configurationVersionRepository, persistPerson("exam.fee.terms." + System.nanoTime()), 3,
                java.math.BigDecimal.valueOf(0.05), 30, 2, LocalDate.now().minusYears(1));
        Invoice installment = persistInvoice(enrollment, FeeType.MONTHLY_INSTALLMENT, 5000, LocalDate.now().minusDays(3));

        assertThat(createAdHocInvoice.create(enrollment.getId(), examFeeRequest()).invoice().getFeeType()).isEqualTo(FeeType.EXAM_FEE);
        assertThat(installment.getDueDate()).isEqualTo(LocalDate.now().minusDays(3));
    }

    @Test
    @Tag("D-154")
    void aReceiptUploadedOnTimeAndUnderReviewDoesNotBlockButOneUploadedLateDoes() {
        ProgramEnrollment onTime = persistProgramEnrollment("exam.fee.ontime." + System.nanoTime());
        LocalDate dueDate = LocalDate.now().minusDays(5);
        underReview(persistInvoice(onTime, FeeType.MONTHLY_INSTALLMENT, 5000, dueDate), dueDate);
        ProgramEnrollment late = persistProgramEnrollment("exam.fee.late." + System.nanoTime());
        underReview(persistInvoice(late, FeeType.MONTHLY_INSTALLMENT, 5000, dueDate), dueDate.plusDays(1));

        assertThat(createAdHocInvoice.create(onTime.getId(), examFeeRequest()).invoice().getFeeType()).isEqualTo(FeeType.EXAM_FEE);
        assertThat(catchThrowable(() -> createAdHocInvoice.create(late.getId(), examFeeRequest()))).isInstanceOf(OverdueDebtBlocksExamFeeException.class);
    }

    private void underReview(Invoice installment, LocalDate uploadedOn) {
        jdbc.update("update invoice set payment_status = 'PAYMENT_UNDER_REVIEW' where id = ?", installment.getId());
        jdbc.update("insert into bank_transfer_receipt (invoice_id, file_path, uploaded_at, status, version) values (?, 'receipt.pdf', ?, 'PENDING_REVIEW', 0)",
                installment.getId(), java.sql.Timestamp.valueOf(uploadedOn.atTime(10, 0)));
    }

    @Test
    void otherAdHocChargesAreNotBlockedByOverdueDebt() {
        ProgramEnrollment enrollment = persistProgramEnrollment("exam.fee.admin.fee." + System.nanoTime());
        persistInvoice(enrollment, FeeType.MONTHLY_INSTALLMENT, 5000, LocalDate.now().minusDays(5));

        Invoice administrative = createAdHocInvoice.create(enrollment.getId(),
                new CreateAdHocInvoiceRequest(FeeType.ADMINISTRATIVE_FEE, ars(800), LocalDate.now(), "Trámite", null, null)).invoice();

        assertThat(administrative.getFeeType()).isEqualTo(FeeType.ADMINISTRATIVE_FEE);
    }
}
