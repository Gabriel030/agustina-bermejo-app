package com.red_escuela.backend.enrollment.service;

import com.red_escuela.backend.academicstructure.entity.Course;
import com.red_escuela.backend.academicstructure.entity.Institution;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.billing.entity.BankTransferReceiptStatus;
import com.red_escuela.backend.billing.events.AdmissionInvoicePaidEvent;
import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.domain.InvoicePaymentStatus;
import com.red_escuela.backend.billing.invoice.repository.InvoiceRepository;
import com.red_escuela.backend.billing.support.BillingTestFixtures;
import com.red_escuela.backend.billing.usecase.paymentreceipt.ApprovePaymentReceiptUseCase;
import com.red_escuela.backend.billing.payment.dto.RegisterCashPaymentRequest;
import com.red_escuela.backend.billing.payment.usecase.PreviewCashPaymentUseCase;
import com.red_escuela.backend.billing.payment.usecase.RegisterCashPaymentUseCase;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.enrollment.dto.CreateCourseEnrollmentRequest;
import com.red_escuela.backend.enrollment.entity.CourseEnrollmentApplication;
import com.red_escuela.backend.enrollment.repository.CourseEnrollmentApplicationRepository;
import com.red_escuela.backend.enrollment.repository.CourseEnrollmentRepository;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.identity.repository.RoleRepository;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.Money;
import com.red_escuela.backend.shared.domain.StatusType;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static com.red_escuela.backend.shared.domain.TestMoney.ars;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@ActiveProfiles("test")
class CourseEnrollmentAdmissionPaymentIT extends AbstractIntegrationTest {
    @Autowired private CourseEnrollmentApplicationService applicationService;
    @Autowired private CourseEnrollmentApplicationRepository applicationRepository;
    @Autowired private CourseEnrollmentRepository courseEnrollmentRepository;
    @Autowired private InvoiceRepository invoiceRepository;
    @Autowired private com.red_escuela.backend.billing.usecase.paymentreceipt.UploadPaymentReceiptUseCase uploadPaymentReceiptUseCase;
    @Autowired private ApprovePaymentReceiptUseCase approvePaymentReceiptUseCase;
    @Autowired private CourseEnrollmentAdmissionPaymentListener admissionPaymentListener;
    @Autowired private RoleRepository roleRepository;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private PreviewCashPaymentUseCase previewCashPaymentUseCase;
    @Autowired private RegisterCashPaymentUseCase registerCashPaymentUseCase;
    @Autowired private EntityManager entityManager;

    private Person persistPerson(String dni) {
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

    private Person persistAdministrative() {
        return transactionTemplate.execute(status -> {
            Person person = persistPerson("ADM" + System.nanoTime());
            person.getRoles().add(roleRepository.findByName("ADMINISTRATIVO").orElseThrow());
            return person;
        });
    }

    private Student persistStudent() {
        return transactionTemplate.execute(status -> {
            Institution institution = new Institution();
            institution.setName("Instituto " + System.nanoTime());
            entityManager.persist(institution);
            Student student = new Student();
            student.setPerson(persistPerson("7" + System.nanoTime()));
            student.setInstitution(institution);
            student.setOnboardingStatus(StudentOnboardingStatus.CONFIRMADO);
            entityManager.persist(student);
            return student;
        });
    }

    private Course persistPricedCourse() {
        return transactionTemplate.execute(status -> {
            Term term = new Term();
            term.setYear(2026);
            term.setStatus(StatusType.ACTIVE);
            entityManager.persist(term);
            Course course = new Course();
            course.setName("Curso " + System.nanoTime());
            course.setTerm(term);
            course.setCapacity(20);
            course.setIsActive(StatusType.ACTIVE);
            entityManager.persist(course);
            com.red_escuela.backend.billing.support.PriceFixtures.persistPriceVersion(null, course.getId(), FeeType.MONTHLY_INSTALLMENT, "Precio de prueba", ars(BigDecimal.valueOf(15000)), LocalDate.now().minusDays(1), persistPerson("DIR" + System.nanoTime()).getId());
            return course;
        });
    }

    private Invoice persistOverdueInstallment(Student student) {
        return BillingTestFixtures.invoice().forStudent(student).ofType(FeeType.MONTHLY_INSTALLMENT).forPeriod(YearMonth.now().minusMonths(1))
                .dueOn(LocalDate.now().minusDays(20)).withBaseAmount(ars(10000)).inState(InvoicePaymentStatus.OVERDUE).build();
    }

    private MultipartFile receiptFile() {
        return new MockMultipartFile("comprobante", "comprobante.pdf", "application/pdf", "contenido de prueba".getBytes());
    }

    private CourseEnrollmentApplication request(Student student, Course course) {
        return applicationService.requestWithPayment(student.getId(), new CreateCourseEnrollmentRequest(course.getId(), null), receiptFile());
    }

    private void awaitEnrollments(Student student, int expected) {
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(courseEnrollmentRepository.findByStudentId(student.getId())).hasSize(expected));
    }

    @Test
    @Tag("D-235")
    void aReceiptAppliedToAnOlderDebtDoesNotStrandTheRequestBecauseTheSecondReceiptThatPaysTheCourseInvoiceEnrollsOnce() {
        Student student = persistStudent();
        Course course = persistPricedCourse();
        persistOverdueInstallment(student);
        Person admin = persistAdministrative();
        CourseEnrollmentApplication application = request(student, course);
        Long invoiceId = application.getInvoiceId();

        var first = approvePaymentReceiptUseCase.approve(application.getPaymentReceipt().getId(), admin);

        assertThat(first.getStatus()).isEqualTo(BankTransferReceiptStatus.APPLIED_TO_DEBT);
        assertThat(courseEnrollmentRepository.findByStudentId(student.getId())).isEmpty();
        var second = uploadPaymentReceiptUseCase.upload(invoiceId, student.getId(), receiptFile());
        approvePaymentReceiptUseCase.approve(second.getId(), admin);

        awaitEnrollments(student, 1);
        assertThat(invoiceRepository.findById(invoiceId).orElseThrow().getPaymentStatus()).isEqualTo(InvoicePaymentStatus.PAID);
        assertThat(applicationRepository.findByStudentIdOrderByIdDesc(student.getId()).get(0).getCourseEnrollment()).isNotNull();
    }

    @Test
    @Tag("D-235")
    void payingTheCourseAdmissionInvoiceEnrollsTheStudent() {
        Student student = persistStudent();
        Course course = persistPricedCourse();
        Person admin = persistAdministrative();
        CourseEnrollmentApplication application = request(student, course);
        Long invoiceId = application.getInvoiceId();

        BillingTestFixtures.registerApprovedBankTransferPayment(invoiceId, admin.getId());

        awaitEnrollments(student, 1);
    }

    @Test
    @Tag("D-235")
    void payingTheCourseAdmissionInvoiceAtTheCounterWithCashEnrollsTheStudent() {
        Student student = persistStudent();
        Course course = persistPricedCourse();
        Person admin = persistAdministrative();
        CourseEnrollmentApplication application = request(student, course);
        Long invoiceId = application.getInvoiceId();
        Money amount = invoiceRepository.findById(invoiceId).orElseThrow().getAmounts().outstandingAmount().value();
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(admin.getId().toString(), null, "ROLE_ADMINISTRATIVO"));
        try {
            String fingerprint = previewCashPaymentUseCase.previewCashPayment(student.getId(), amount).fingerprint();
            registerCashPaymentUseCase.registerCashPayment(student.getId(),
                    new RegisterCashPaymentRequest(amount, null, null, null, fingerprint), "cash-admission-" + System.nanoTime());
        } finally {
            SecurityContextHolder.clearContext();
        }

        awaitEnrollments(student, 1);
        assertThat(invoiceRepository.findById(invoiceId).orElseThrow().getPaymentStatus()).isEqualTo(InvoicePaymentStatus.PAID);
        assertThat(applicationRepository.findByStudentIdOrderByIdDesc(student.getId()).get(0).getCourseEnrollment()).isNotNull();
    }

    @Test
    @Tag("D-235")
    void redeliveringTheAdmissionPaidEventNeverEnrollsTwice() {
        Student student = persistStudent();
        Course course = persistPricedCourse();
        Person admin = persistAdministrative();
        CourseEnrollmentApplication application = request(student, course);
        Long invoiceId = application.getInvoiceId();
        approvePaymentReceiptUseCase.approve(application.getPaymentReceipt().getId(), admin);
        awaitEnrollments(student, 1);

        transactionTemplate.executeWithoutResult(status -> admissionPaymentListener.confirmCourseEnrollmentOfPaidAdmission(AdmissionInvoicePaidEvent.paidFor(invoiceId)));
        transactionTemplate.executeWithoutResult(status -> admissionPaymentListener.confirmCourseEnrollmentOfPaidAdmission(AdmissionInvoicePaidEvent.paidFor(invoiceId)));

        assertThat(courseEnrollmentRepository.findByStudentId(student.getId())).hasSize(1);
    }

    @Test
    @Tag("D-235")
    void approvingTheFirstInstallmentReceiptEnrollsTheStudentGrantsTheRoleAndLinksTheInvoice() {
        Student student = persistStudent();
        Course course = persistPricedCourse();
        CourseEnrollmentApplication application = request(student, course);
        Long invoiceId = application.getInvoiceId();

        approvePaymentReceiptUseCase.approve(application.getPaymentReceipt().getId(), persistAdministrative());

        awaitEnrollments(student, 1);
        Long enrollmentId = courseEnrollmentRepository.findByStudentId(student.getId()).get(0).getId();
        assertThat(invoiceRepository.findInvoiceWithLinesById(invoiceId).orElseThrow().findCourseEnrollmentId()).contains(enrollmentId);
        List<String> roleNames = transactionTemplate.execute(status -> entityManager.find(Student.class, student.getId()).getPerson().getRoles().stream()
                .map(com.red_escuela.backend.identity.entity.Role::getName).toList());
        assertThat(roleNames).contains("ALUMNO");
    }

    @Test
    @Tag("D-235")
    void studentsInProgressCountTowardsCourseCapacityWhenThePaidAdmissionEnrolls() {
        Course course = persistPricedCourse();
        transactionTemplate.executeWithoutResult(status -> {
            course.setCapacity(1);
            entityManager.merge(course);
        });
        Student occupant = persistStudent();
        transactionTemplate.executeWithoutResult(status -> {
            com.red_escuela.backend.enrollment.entity.CourseEnrollment enrollment = new com.red_escuela.backend.enrollment.entity.CourseEnrollment();
            enrollment.setStudent(entityManager.find(Student.class, occupant.getId()));
            enrollment.setCourse(entityManager.find(Course.class, course.getId()));
            enrollment.setStatus(com.red_escuela.backend.enrollment.entity.EnrollmentStatus.IN_PROGRESS);
            enrollment.setEnrollmentDate(java.time.LocalDateTime.now().minusDays(1));
            entityManager.persist(enrollment);
        });
        Student student = persistStudent();
        CourseEnrollmentApplication application = request(student, course);

        approvePaymentReceiptUseCase.approve(application.getPaymentReceipt().getId(), persistAdministrative());

        awaitEnrollments(student, 1);
        assertThat(courseEnrollmentRepository.findByStudentId(student.getId()).get(0).getStatus())
                .isEqualTo(com.red_escuela.backend.enrollment.entity.EnrollmentStatus.WAITLISTED);
    }
}
