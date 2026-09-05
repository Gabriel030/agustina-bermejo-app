package com.red_escuela.backend.service;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.entity.Address;
import com.red_escuela.backend.entity.Course;
import com.red_escuela.backend.entity.CourseEnrollment;
import com.red_escuela.backend.entity.EnrollmentStatus;
import com.red_escuela.backend.entity.FeeType;
import com.red_escuela.backend.entity.Institution;
import com.red_escuela.backend.entity.Invoice;
import com.red_escuela.backend.entity.InvoiceStatus;
import com.red_escuela.backend.entity.Person;
import com.red_escuela.backend.entity.PaymentReceipt;
import com.red_escuela.backend.entity.PaymentReceiptStatus;
import com.red_escuela.backend.entity.Program;
import com.red_escuela.backend.entity.ProgramEnrollment;
import com.red_escuela.backend.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.entity.StatusType;
import com.red_escuela.backend.entity.Student;
import com.red_escuela.backend.entity.StudentOnboardingStatus;
import com.red_escuela.backend.entity.Term;
import com.red_escuela.backend.exception.InvoiceNotFoundException;
import com.red_escuela.backend.exception.InvoiceNotPayableException;
import com.red_escuela.backend.exception.PaymentReceiptAlreadyPendingException;
import com.red_escuela.backend.repository.AddressRepository;
import com.red_escuela.backend.repository.CourseEnrollmentRepository;
import com.red_escuela.backend.repository.CourseRepository;
import com.red_escuela.backend.repository.InstitutionRepository;
import com.red_escuela.backend.repository.InvoiceRepository;
import com.red_escuela.backend.repository.PersonRepository;
import com.red_escuela.backend.repository.ProgramEnrollmentRepository;
import com.red_escuela.backend.repository.ProgramRepository;
import com.red_escuela.backend.repository.StudentRepository;
import com.red_escuela.backend.repository.TermRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Fase 6.2, tests 140-145: comprobante subido por el Alumno.
@SpringBootTest
@ActiveProfiles("test")
class PaymentReceiptServiceTest extends AbstractIntegrationTest {

    @Autowired
    private PaymentReceiptService paymentReceiptService;

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
    private CourseRepository courseRepository;

    @Autowired
    private CourseEnrollmentRepository courseEnrollmentRepository;

    @Autowired
    private TermRepository termRepository;

    @Autowired
    private com.red_escuela.backend.repository.PaymentReceiptRepository paymentReceiptRepository;

    @Autowired
    private ProgramRepository programRepository;

    @Autowired
    private ProgramEnrollmentRepository programEnrollmentRepository;

    private Person persistPerson(String username) {
        Address address = addressRepository.save(new Address());
        Person person = new Person();
        person.setUsername(username);
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

    private CourseEnrollment persistCourseEnrollment(Student student) {
        Term term = new Term();
        term.setYear(2026);
        term.setStatus(StatusType.ACTIVE);
        term = termRepository.save(term);

        Course course = new Course();
        course.setName("Curso de prueba " + System.nanoTime());
        course.setTerm(term);
        course.setIsActive(StatusType.ACTIVE);
        course = courseRepository.save(course);

        CourseEnrollment enrollment = new CourseEnrollment();
        enrollment.setStudent(student);
        enrollment.setCourse(course);
        enrollment.setStatus(EnrollmentStatus.ENROLLED);
        enrollment.setEnrollmentDate(LocalDateTime.now());
        return courseEnrollmentRepository.save(enrollment);
    }

    private Invoice persistInvoice(CourseEnrollment enrollment, BigDecimal amount, InvoiceStatus status) {
        Invoice invoice = new Invoice();
        invoice.setCourseEnrollment(enrollment);
        invoice.setFeeType(FeeType.RECURRING_FEE);
        invoice.setPeriod(YearMonth.now());
        invoice.setDueDate(LocalDate.now());
        invoice.setBaseAmount(amount);
        invoice.setStatus(status);
        return invoiceRepository.save(invoice);
    }

    private ProgramEnrollment persistProgramEnrollment(Student student) {
        Program program = new Program();
        program.setName("Carrera de prueba " + System.nanoTime());
        program.setTotalYears(3);
        program.setTotalCreditsRequired(0);
        program = programRepository.save(program);

        ProgramEnrollment enrollment = new ProgramEnrollment();
        enrollment.setStudent(student);
        enrollment.setProgram(program);
        enrollment.setStatus(ProgramEnrollmentStatus.ENROLLED);
        enrollment.setEnrollmentDate(LocalDateTime.now());
        return programEnrollmentRepository.save(enrollment);
    }

    private Invoice persistInvoice(ProgramEnrollment enrollment, BigDecimal amount, InvoiceStatus status) {
        Invoice invoice = new Invoice();
        invoice.setProgramEnrollment(enrollment);
        invoice.setFeeType(FeeType.RECURRING_FEE);
        invoice.setPeriod(YearMonth.now());
        invoice.setDueDate(LocalDate.now());
        invoice.setBaseAmount(amount);
        invoice.setStatus(status);
        return invoiceRepository.save(invoice);
    }

    private MockMultipartFile receiptFile() {
        return new MockMultipartFile("comprobante", "comprobante.pdf", "application/pdf", "contenido de prueba".getBytes());
    }

    @Test
    void uploadingAReceiptLeavesItPendingReviewWithoutTouchingTheInvoice() {
        Student student = persistStudent("97000001");
        CourseEnrollment enrollment = persistCourseEnrollment(student);
        Invoice invoice = persistInvoice(enrollment, BigDecimal.valueOf(10000), InvoiceStatus.PENDING);

        PaymentReceipt receipt = paymentReceiptService.subir(invoice.getId(), student.getId(), receiptFile());

        assertThat(receipt.getStatus()).isEqualTo(PaymentReceiptStatus.PENDING_REVIEW);
        assertThat(invoiceRepository.findById(invoice.getId()).orElseThrow().getStatus()).isEqualTo(InvoiceStatus.PENDING);
    }

    @Test
    void cannotUploadASecondReceiptWhileTheFirstIsStillPending() {
        Student student = persistStudent("97000002");
        CourseEnrollment enrollment = persistCourseEnrollment(student);
        Invoice invoice = persistInvoice(enrollment, BigDecimal.valueOf(10000), InvoiceStatus.PENDING);
        paymentReceiptService.subir(invoice.getId(), student.getId(), receiptFile());

        assertThatThrownBy(() -> paymentReceiptService.subir(invoice.getId(), student.getId(), receiptFile()))
                .isInstanceOf(PaymentReceiptAlreadyPendingException.class);
    }

    @Test
    void approvingCreatesARealPaymentAndMarksTheInvoiceAsPaid() {
        Person admin = persistPerson("admin.receipt3." + System.nanoTime());
        Student student = persistStudent("97000003");
        CourseEnrollment enrollment = persistCourseEnrollment(student);
        Invoice invoice = persistInvoice(enrollment, BigDecimal.valueOf(10000), InvoiceStatus.PENDING);
        PaymentReceipt receipt = paymentReceiptService.subir(invoice.getId(), student.getId(), receiptFile());

        PaymentReceipt approved = paymentReceiptService.aprobar(receipt.getId(), admin);

        assertThat(approved.getStatus()).isEqualTo(PaymentReceiptStatus.APPROVED);
        assertThat(invoiceRepository.findById(invoice.getId()).orElseThrow().getStatus()).isEqualTo(InvoiceStatus.PAID);
    }

    @Test
    void rejectingWithAReasonAllowsTheStudentToRetry() {
        Person admin = persistPerson("admin.receipt4." + System.nanoTime());
        Student student = persistStudent("97000004");
        CourseEnrollment enrollment = persistCourseEnrollment(student);
        Invoice invoice = persistInvoice(enrollment, BigDecimal.valueOf(10000), InvoiceStatus.PENDING);
        PaymentReceipt receipt = paymentReceiptService.subir(invoice.getId(), student.getId(), receiptFile());

        PaymentReceipt rejected = paymentReceiptService.reject(receipt.getId(), "Comprobante ilegible", admin);
        assertThat(rejected.getStatus()).isEqualTo(PaymentReceiptStatus.REJECTED);
        assertThat(rejected.getRejectionReason()).isEqualTo("Comprobante ilegible");

        // Sin límite de reintentos: la Invoice vuelve a estar disponible.
        PaymentReceipt retry = paymentReceiptService.subir(invoice.getId(), student.getId(), receiptFile());
        assertThat(retry.getStatus()).isEqualTo(PaymentReceiptStatus.PENDING_REVIEW);
    }

    @Test
    void aStudentCannotUploadAReceiptForSomeoneElsesInvoice() {
        Student owner = persistStudent("97000005");
        Student intruder = persistStudent("97000006");
        CourseEnrollment enrollment = persistCourseEnrollment(owner);
        Invoice invoice = persistInvoice(enrollment, BigDecimal.valueOf(10000), InvoiceStatus.PENDING);

        assertThatThrownBy(() -> paymentReceiptService.subir(invoice.getId(), intruder.getId(), receiptFile()))
                .isInstanceOf(InvoiceNotFoundException.class);
    }

    // Regresión (auditoría 2026-09-04): sin @Version en PaymentReceipt, aprobar() y reject()
    // concurrentes sobre el mismo comprobante podían "ganar" ambos — uno dejaba un Payment
    // CONFIRMED real y la Invoice en PAID, el otro pisaba el status a REJECTED sin detectar el
    // conflicto. Con el lock optimista, exactamente uno de los dos tiene que ganar.
    @Test
    void aprobarYRechazarEnParaleloSobreElMismoComprobanteSoloUnoGana() throws Exception {
        Person admin1 = persistPerson("admin.receipt8a." + System.nanoTime());
        Person admin2 = persistPerson("admin.receipt8b." + System.nanoTime());
        Student student = persistStudent("97000008");
        CourseEnrollment enrollment = persistCourseEnrollment(student);
        Invoice invoice = persistInvoice(enrollment, BigDecimal.valueOf(10000), InvoiceStatus.PENDING);
        PaymentReceipt receipt = paymentReceiptService.subir(invoice.getId(), student.getId(), receiptFile());

        CountDownLatch listos = new CountDownLatch(2);
        CountDownLatch arranca = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        Callable<PaymentReceipt> intentoAprobar = () -> {
            listos.countDown();
            arranca.await();
            return paymentReceiptService.aprobar(receipt.getId(), admin1);
        };
        Callable<PaymentReceipt> intentoRechazar = () -> {
            listos.countDown();
            arranca.await();
            return paymentReceiptService.reject(receipt.getId(), "Comprobante ilegible", admin2);
        };

        Future<PaymentReceipt> resultadoAprobar = executor.submit(intentoAprobar);
        Future<PaymentReceipt> resultadoRechazar = executor.submit(intentoRechazar);
        listos.await(5, TimeUnit.SECONDS);
        arranca.countDown();

        int exitos = 0;
        int conflictos = 0;
        for (Future<PaymentReceipt> resultado : List.of(resultadoAprobar, resultadoRechazar)) {
            try {
                resultado.get(30, TimeUnit.SECONDS);
                exitos++;
            } catch (ExecutionException e) {
                assertThat(e.getCause()).isInstanceOf(org.springframework.orm.ObjectOptimisticLockingFailureException.class);
                conflictos++;
            }
        }
        executor.shutdown();

        assertThat(exitos).isEqualTo(1);
        assertThat(conflictos).isEqualTo(1);

        // El estado final tiene que ser consistente entre sí: si ganó aprobar(), la Invoice está
        // PAID; si ganó reject(), la Invoice sigue PENDING y no existe ningún Payment real.
        PaymentReceipt reloaded = paymentReceiptRepository.findById(receipt.getId()).orElseThrow();
        Invoice reloadedInvoice = invoiceRepository.findById(invoice.getId()).orElseThrow();
        if (reloaded.getStatus() == PaymentReceiptStatus.APPROVED) {
            assertThat(reloadedInvoice.getStatus()).isEqualTo(InvoiceStatus.PAID);
        } else {
            assertThat(reloaded.getStatus()).isEqualTo(PaymentReceiptStatus.REJECTED);
            assertThat(reloadedInvoice.getStatus()).isEqualTo(InvoiceStatus.PENDING);
        }
    }

    @Test
    void uploadingAReceiptOnAnAlreadyPaidInvoiceIsRejected() {
        Student student = persistStudent("97000007");
        CourseEnrollment enrollment = persistCourseEnrollment(student);
        Invoice invoice = persistInvoice(enrollment, BigDecimal.valueOf(10000), InvoiceStatus.PAID);

        assertThatThrownBy(() -> paymentReceiptService.subir(invoice.getId(), student.getId(), receiptFile()))
                .isInstanceOf(InvoiceNotPayableException.class);
    }

    @Test
    void findOwnIncludesReceiptsFromProgramEnrollmentInvoicesNotOnlyCourseEnrollment() {
        Student student = persistStudent("97000009");
        ProgramEnrollment programEnrollment = persistProgramEnrollment(student);
        Invoice invoice = persistInvoice(programEnrollment, BigDecimal.valueOf(10000), InvoiceStatus.PENDING);
        PaymentReceipt receipt = paymentReceiptService.subir(invoice.getId(), student.getId(), receiptFile());

        List<PaymentReceipt> ownReceipts = paymentReceiptService.findOwn(student.getId());

        assertThat(ownReceipts).extracting(PaymentReceipt::getId).contains(receipt.getId());
    }
}
