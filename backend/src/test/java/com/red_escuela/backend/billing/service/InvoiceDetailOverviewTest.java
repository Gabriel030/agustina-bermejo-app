package com.red_escuela.backend.billing.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.red_escuela.backend.academicstructure.entity.Course;
import com.red_escuela.backend.academicstructure.entity.Institution;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.academicstructure.repository.CourseRepository;
import com.red_escuela.backend.academicstructure.repository.InstitutionRepository;
import com.red_escuela.backend.academicstructure.repository.TermRepository;
import com.red_escuela.backend.billing.dto.AdminEnrollmentBillingStatusResponse;
import com.red_escuela.backend.billing.dto.AdminInvoicePaymentResponse;
import com.red_escuela.backend.billing.dto.AdminInvoiceResponse;
import com.red_escuela.backend.billing.dto.MeEnrollmentBillingStatusResponse;
import com.red_escuela.backend.billing.dto.MeInvoicePaymentResponse;
import com.red_escuela.backend.billing.dto.PaymentAllocationRequest;
import com.red_escuela.backend.billing.dto.RegisterPaymentRequest;
import com.red_escuela.backend.billing.entity.FeeType;
import com.red_escuela.backend.billing.entity.Invoice;
import com.red_escuela.backend.billing.entity.InvoiceStatus;
import com.red_escuela.backend.billing.entity.PaymentMethod;
import com.red_escuela.backend.billing.entity.PaymentReceipt;
import com.red_escuela.backend.billing.entity.PaymentReceiptStatus;
import com.red_escuela.backend.billing.repository.InvoiceRepository;
import com.red_escuela.backend.billing.repository.PaymentReceiptRepository;
import com.red_escuela.backend.billing.usecase.invoice.GetOwnCourseEnrollmentBillingStatusUseCase;
import com.red_escuela.backend.billing.usecase.invoice.GetStudentBillingOverviewUseCase;
import com.red_escuela.backend.billing.usecase.payment.RegisterPaymentUseCase;
import com.red_escuela.backend.billing.usecase.paymentreceipt.ApprovePaymentReceiptUseCase;
import com.red_escuela.backend.billing.usecase.paymentreceipt.RejectPaymentReceiptUseCase;
import com.red_escuela.backend.billing.usecase.paymentreceipt.UploadPaymentReceiptUseCase;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.enrollment.entity.CourseEnrollment;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.repository.CourseEnrollmentRepository;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.billing.exception.NotOwnEnrollmentException;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.identity.repository.PersonRepository;
import com.red_escuela.backend.identity.repository.StudentRepository;
import com.red_escuela.backend.identity.repository.AddressRepository;
import com.red_escuela.backend.shared.domain.Money;
import com.red_escuela.backend.shared.persistence.QueryCountAssertions;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.List;

import static com.red_escuela.backend.shared.domain.TestMoney.ars;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class InvoiceDetailOverviewTest extends AbstractIntegrationTest {
    @Autowired
    private GetStudentBillingOverviewUseCase getStudentBillingOverviewUseCase;

    @Autowired
    private GetOwnCourseEnrollmentBillingStatusUseCase getOwnCourseEnrollmentBillingStatusUseCase;

    @Autowired
    private RegisterPaymentUseCase registerPaymentUseCase;

    @Autowired
    private UploadPaymentReceiptUseCase uploadPaymentReceiptUseCase;

    @Autowired
    private ApprovePaymentReceiptUseCase approvePaymentReceiptUseCase;

    @Autowired
    private RejectPaymentReceiptUseCase rejectPaymentReceiptUseCase;

    @Autowired
    private PaymentReceiptRepository paymentReceiptRepository;

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
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private ObjectMapper objectMapper;

    private Person persistPerson(String username, String name, String lastname) {
        Address address = addressRepository.save(new Address());
        Person person = new Person();
        person.setUsername(username);
        person.setEmail(username + "@example.com");
        person.setName(name);
        person.setLastname(lastname);
        person.setAddress(address);
        person.setStatus(StatusType.ACTIVE);
        return personRepository.save(person);
    }

    private Student persistStudent() {
        Institution institution = new Institution();
        institution.setName("Instituto de prueba " + System.nanoTime());
        institution = institutionRepository.save(institution);
        Student student = new Student();
        student.setPerson(persistPerson("alumno.detail." + System.nanoTime(), "Lucia", "Perez"));
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

    private Invoice persistInvoice(CourseEnrollment enrollment, Money amount, int monthOffset) {
        Invoice invoice = new Invoice();
        invoice.setCourseEnrollment(enrollment);
        invoice.setFeeType(FeeType.RECURRING_FEE);
        invoice.setPeriod(YearMonth.of(2030, 1).plusMonths(monthOffset));
        invoice.setDueDate(LocalDate.of(2030, 1, 10).plusMonths(monthOffset));
        invoice.setBaseAmount(amount);
        invoice.setStatus(InvoiceStatus.PENDING);
        return invoiceRepository.save(invoice);
    }

    private MockMultipartFile receiptFile() {
        return new MockMultipartFile("comprobante", "comprobante.pdf", "application/pdf", "contenido".getBytes());
    }

    private RegisterPaymentRequest request(PaymentMethod method, String reference, Integer installments, String notes,
                                           PaymentAllocationRequest... allocations) {
        return new RegisterPaymentRequest(Arrays.asList(allocations), method, false, null, reference, installments, notes);
    }

    private AdminInvoiceResponse adminInvoice(Student student, Long invoiceId) {
        return getStudentBillingOverviewUseCase.get(student.getId()).stream()
                .flatMap(enrollment -> enrollment.invoices().stream())
                .filter(invoice -> invoice.id().equals(invoiceId))
                .findFirst().orElseThrow();
    }

    @Test
    void theOverviewListsEachPaymentOfAnInvoiceNewestFirstWithItsRecorderAndNotes() throws Exception {
        Person recorder = persistPerson("admin.detail1." + System.nanoTime(), "Ana", "Ruiz");
        Student student = persistStudent();
        Invoice invoice = persistInvoice(persistCourseEnrollment(student), ars(10000), 0);
        registerPaymentUseCase.register(request(PaymentMethod.CASH, null, null, "Seña",
                new PaymentAllocationRequest(invoice.getId(), ars(3000))), recorder);
        Thread.sleep(5);
        registerPaymentUseCase.register(request(PaymentMethod.TRANSFER, "TRF-998", null, null,
                new PaymentAllocationRequest(invoice.getId(), ars(2000))), recorder);

        AdminInvoiceResponse response = adminInvoice(student, invoice.getId());

        assertThat(response.status()).isEqualTo("PENDING");
        assertThat(response.remainingBalance()).isEqualTo(ars(5000));
        assertThat(response.payments()).hasSize(2);
        AdminInvoicePaymentResponse newest = response.payments().get(0);
        assertThat(newest.method()).isEqualTo("TRANSFER");
        assertThat(newest.externalReference()).isEqualTo("TRF-998");
        assertThat(newest.amountApplied()).isEqualTo(ars(2000));
        assertThat(newest.paymentTotal()).isEqualTo(ars(2000));
        assertThat(newest.registeredByName()).isEqualTo("Ana Ruiz");
        assertThat(newest.status()).isEqualTo("CONFIRMED");
        assertThat(response.payments().get(1).notes()).isEqualTo("Seña");
        assertThat(response.paymentDate()).isEqualTo(newest.paidAt());
        assertThat(response.receipts()).isEmpty();
    }

    @Test
    void aPaymentCoveringTwoInstallmentsReportsTheAppliedAmountAndTheTotalOnEach() {
        Person recorder = persistPerson("admin.detail2." + System.nanoTime(), "Ana", "Ruiz");
        Student student = persistStudent();
        CourseEnrollment enrollment = persistCourseEnrollment(student);
        Invoice first = persistInvoice(enrollment, ars(10000), 0);
        Invoice second = persistInvoice(enrollment, ars(10000), 1);
        registerPaymentUseCase.register(request(PaymentMethod.CARD, "LOTE-1", 3, null,
                new PaymentAllocationRequest(first.getId(), ars(10000)),
                new PaymentAllocationRequest(second.getId(), ars(4000))), recorder);

        AdminInvoicePaymentResponse onSecond = adminInvoice(student, second.getId()).payments().get(0);

        assertThat(onSecond.amountApplied()).isEqualTo(ars(4000));
        assertThat(onSecond.paymentTotal()).isEqualTo(ars(14000));
        assertThat(onSecond.cardInstallments()).isEqualTo(3);
        assertThat(onSecond.cardType()).isNull();
    }

    @Test
    @Tag("D-081")
    void approvingAReceiptLinksItToThePaymentItGeneratedAndTheOverviewExposesTheLink() {
        Person reviewer = persistPerson("admin.detail3." + System.nanoTime(), "Laura", "Gomez");
        Student student = persistStudent();
        Invoice invoice = persistInvoice(persistCourseEnrollment(student), ars(10000), 0);
        PaymentReceipt receipt = uploadPaymentReceiptUseCase.upload(invoice.getId(), student.getId(), receiptFile());

        PaymentReceipt approved = approvePaymentReceiptUseCase.approve(receipt.getId(), reviewer);

        PaymentReceipt reloaded = paymentReceiptRepository.findByInvoiceIdsWithPeople(List.of(invoice.getId())).get(0);
        assertThat(reloaded.getPayment()).isNotNull();
        assertThat(reloaded.getPayment().getId()).isNotNull();
        AdminInvoiceResponse response = adminInvoice(student, invoice.getId());
        assertThat(response.payments()).hasSize(1);
        assertThat(response.payments().get(0).receiptId()).isEqualTo(approved.getId());
        assertThat(response.receipts()).hasSize(1);
        assertThat(response.receipts().get(0).status()).isEqualTo("APPROVED");
        assertThat(response.receipts().get(0).reviewedByName()).isEqualTo("Laura Gomez");
        assertThat(response.receipts().get(0).uploadedByName()).isEqualTo("Lucia Perez");
    }

    @Test
    @Tag("D-081")
    void receiptsInEveryStateAreListedAndARejectedOneKeepsItsReasonWithoutLinkingAPayment() {
        Person reviewer = persistPerson("admin.detail4." + System.nanoTime(), "Laura", "Gomez");
        Student student = persistStudent();
        Invoice invoice = persistInvoice(persistCourseEnrollment(student), ars(10000), 0);
        PaymentReceipt rejected = uploadPaymentReceiptUseCase.upload(invoice.getId(), student.getId(), receiptFile());
        rejectPaymentReceiptUseCase.reject(rejected.getId(), "Ilegible", reviewer);
        uploadPaymentReceiptUseCase.upload(invoice.getId(), student.getId(), receiptFile());

        AdminInvoiceResponse response = adminInvoice(student, invoice.getId());

        assertThat(response.payments()).isEmpty();
        assertThat(response.receipts()).extracting(r -> r.status())
                .containsExactlyInAnyOrder(PaymentReceiptStatus.REJECTED.name(), PaymentReceiptStatus.PENDING_REVIEW.name());
        assertThat(response.receipts()).filteredOn(r -> r.status().equals("REJECTED"))
                .singleElement().satisfies(r -> assertThat(r.rejectionReason()).isEqualTo("Ilegible"));
    }

    @Test
    void theJsonNeverExposesTheStoredFilePathOrTheInternalPaymentFieldsToTheStudent() throws Exception {
        Person recorder = persistPerson("admin.detail5." + System.nanoTime(), "Ana", "Ruiz");
        Student student = persistStudent();
        CourseEnrollment enrollment = persistCourseEnrollment(student);
        Invoice invoice = persistInvoice(enrollment, ars(10000), 0);
        PaymentReceipt receipt = uploadPaymentReceiptUseCase.upload(invoice.getId(), student.getId(), receiptFile());
        approvePaymentReceiptUseCase.approve(receipt.getId(), recorder);

        JsonNode admin = objectMapper.valueToTree(adminInvoice(student, invoice.getId()));
        MeEnrollmentBillingStatusResponse own = getOwnCourseEnrollmentBillingStatusUseCase.get(student.getId(), enrollment.getId());
        JsonNode me = objectMapper.valueToTree(own.invoices().get(0));

        assertThat(admin.get("receipts").get(0).has("filePath")).isFalse();
        assertThat(admin.get("receipts").get(0).has("fileName")).isTrue();
        assertThat(admin.get("payments").get(0).has("registeredByName")).isTrue();
        assertThat(me.get("payments").get(0).has("registeredByName")).isFalse();
        assertThat(me.get("payments").get(0).has("notes")).isFalse();
        assertThat(me.get("receipts").get(0).has("filePath")).isFalse();
        assertThat(me.get("receipts").get(0).has("reviewedByName")).isFalse();
        assertThat(me.get("receipts").get(0).has("uploadedByName")).isFalse();
        MeInvoicePaymentResponse payment = own.invoices().get(0).payments().get(0);
        assertThat(payment.method()).isEqualTo("TRANSFER");
        assertThat(payment.receiptId()).isEqualTo(receipt.getId());
    }

    @Test
    void aStudentCannotReadTheInstallmentsOfSomeoneElsesEnrollment() {
        Student owner = persistStudent();
        Student intruder = persistStudent();
        CourseEnrollment enrollment = persistCourseEnrollment(owner);

        assertThatThrownBy(() -> getOwnCourseEnrollmentBillingStatusUseCase.get(intruder.getId(), enrollment.getId()))
                .isInstanceOf(NotOwnEnrollmentException.class);
    }

    @Test
    void theOverviewQueryCountDoesNotGrowWithTheNumberOfInstallments() {
        Person recorder = persistPerson("admin.detail6." + System.nanoTime(), "Ana", "Ruiz");
        Student student = persistStudent();
        CourseEnrollment enrollment = persistCourseEnrollment(student);
        for (int i = 0; i < 24; i++) {
            Invoice invoice = persistInvoice(enrollment, ars(10000), i);
            if (i % 2 == 0) {
                registerPaymentUseCase.register(request(PaymentMethod.CASH, null, null, null,
                        new PaymentAllocationRequest(invoice.getId(), ars(10000))), recorder);
            } else {
                uploadPaymentReceiptUseCase.upload(invoice.getId(), student.getId(), receiptFile());
            }
        }

        List<AdminEnrollmentBillingStatusResponse> overview = QueryCountAssertions.assertAtMostQueries(
                entityManagerFactory, 12, () -> getStudentBillingOverviewUseCase.get(student.getId()));

        assertThat(overview).hasSize(1);
        assertThat(overview.get(0).invoices()).hasSize(24);
        assertThat(overview.get(0).invoices().get(0).payments()).hasSize(1);
        assertThat(overview.get(0).invoices().get(1).receipts()).hasSize(1);
    }
}
