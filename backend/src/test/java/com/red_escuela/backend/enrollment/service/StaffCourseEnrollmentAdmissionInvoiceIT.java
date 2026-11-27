package com.red_escuela.backend.enrollment.service;

import com.red_escuela.backend.academicstructure.entity.Course;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.repository.InvoiceRepository;
import com.red_escuela.backend.billing.operation.PaymentVerification;
import com.red_escuela.backend.billing.usecase.paymentreceipt.ApprovePaymentReceiptUseCase;
import com.red_escuela.backend.billing.usecase.paymentreceipt.UploadPaymentReceiptUseCase;
import com.red_escuela.backend.identity.repository.RoleRepository;
import org.springframework.mock.web.MockMultipartFile;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.enrollment.api.WithdrawalCause;
import com.red_escuela.backend.enrollment.dto.CreateCourseEnrollmentRequest;
import com.red_escuela.backend.enrollment.entity.CourseEnrollment;
import com.red_escuela.backend.enrollment.entity.CourseEnrollmentApplication;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.exception.AlreadyEnrolledInCourseException;
import com.red_escuela.backend.enrollment.repository.CourseEnrollmentApplicationRepository;
import com.red_escuela.backend.enrollment.repository.CourseEnrollmentRepository;
import com.red_escuela.backend.identity.entity.Institution;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.testsupport.TermFixtures;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

import static com.red_escuela.backend.shared.domain.TestMoney.ars;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@ActiveProfiles("test")
class StaffCourseEnrollmentAdmissionInvoiceIT extends AbstractIntegrationTest {
    @Autowired private CourseEnrollmentApplicationService applicationService;
    @Autowired private CourseEnrollmentService courseEnrollmentService;
    @Autowired private CourseEnrollmentApplicationRepository applicationRepository;
    @Autowired private CourseEnrollmentRepository courseEnrollmentRepository;
    @Autowired private InvoiceRepository invoiceRepository;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private EntityManager entityManager;
    @Autowired private UploadPaymentReceiptUseCase uploadPaymentReceiptUseCase;
    @Autowired private ApprovePaymentReceiptUseCase approvePaymentReceiptUseCase;
    @Autowired private RoleRepository roleRepository;

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

    private Student persistStudent() {
        return transactionTemplate.execute(status -> {
            Institution institution = new Institution();
            institution.setName("Instituto " + System.nanoTime());
            entityManager.persist(institution);
            Student student = new Student();
            student.setPerson(persistPerson("8" + System.nanoTime()));
            student.setInstitution(institution);
            student.setOnboardingStatus(StudentOnboardingStatus.CONFIRMADO);
            entityManager.persist(student);
            return student;
        });
    }

    private Long persistAdministrativeId() {
        return persistAdministrative().getId();
    }

    private Person persistAdministrative() {
        return transactionTemplate.execute(status -> {
            Person person = persistPerson("ADM" + System.nanoTime());
            person.getRoles().add(roleRepository.findByName("ADMINISTRATIVO").orElseThrow());
            return person;
        });
    }

    private Long uploadAdmissionReceipt(CourseEnrollment waiting) {
        Long invoiceId = applicationRepository.findInvoiceIdsByCourseEnrollmentId(waiting.getId()).get(0);
        return uploadPaymentReceiptUseCase.upload(invoiceId, waiting.getStudent().getId(),
                new MockMultipartFile("comprobante", "comprobante.pdf", "application/pdf", "%PDF-1.4 contenido de prueba".getBytes())).getId();
    }

    private Course persistPricedCourse(int capacity) {
        return transactionTemplate.execute(status -> {
            Term term = TermFixtures.newTerm();
            term.setYear(2026);
            term.setStatus(StatusType.ACTIVE);
            entityManager.persist(term);
            Course course = new Course();
            course.setVisibleInCatalog(true);
            course.setName("Curso " + System.nanoTime());
            course.setTerm(term);
            course.setCapacity(capacity);
            course.setIsActive(StatusType.ACTIVE);
            entityManager.persist(course);
            com.red_escuela.backend.billing.support.PriceFixtures.persistPriceVersion(null, course.getId(), FeeType.MONTHLY_INSTALLMENT, "Precio de prueba",
                    ars(BigDecimal.valueOf(15000)), LocalDate.now().minusDays(1), persistPerson("DIR" + System.nanoTime()).getId());
            return course;
        });
    }

    private CourseEnrollment enrollByStaff(Student student, Course course) {
        return applicationService.enrollByStaff(student.getId(), new CreateCourseEnrollmentRequest(course.getId(), null));
    }

    private EnrollmentStatus statusOf(Long enrollmentId) {
        return courseEnrollmentRepository.findById(enrollmentId).orElseThrow().getStatus();
    }

    private void releaseSeat(CourseEnrollment seated) {
        courseEnrollmentService.withdraw(seated.getId(), new EnrollmentWithdrawal(null, persistAdministrativeId(), WithdrawalCause.VOLUNTARY));
    }

    @Test
    void staffEnrollmentWithAvailableSeatIsEnrolledWithoutApplicationOrAdmissionInvoice() {
        Student student = persistStudent();
        Course course = persistPricedCourse(5);

        CourseEnrollment enrollment = enrollByStaff(student, course);

        assertThat(statusOf(enrollment.getId())).isEqualTo(EnrollmentStatus.ENROLLED);
        assertThat(applicationRepository.findByStudentIdOrderByIdDesc(student.getId())).isEmpty();
    }

    @Test
    void staffEnrollmentWithoutSeatIsWaitlistedWithAnApplicationLinkedToAnAdmissionInvoice() {
        Course course = persistPricedCourse(1);
        enrollByStaff(persistStudent(), course);
        Student waiting = persistStudent();

        CourseEnrollment enrollment = enrollByStaff(waiting, course);

        assertThat(statusOf(enrollment.getId())).isEqualTo(EnrollmentStatus.WAITLISTED);
        List<CourseEnrollmentApplication> applications = applicationRepository.findByStudentIdOrderByIdDesc(waiting.getId());
        assertThat(applications).hasSize(1);
        assertThat(applicationRepository.findInvoiceIdsByCourseEnrollmentId(enrollment.getId())).hasSize(1);
        Invoice invoice = invoiceRepository.findById(applications.get(0).getInvoiceId()).orElseThrow();
        assertThat(invoice.getAmounts().outstandingAmount().value()).isEqualTo(ars(BigDecimal.valueOf(15000)));
    }

    @Test
    void retryingTheStaffEnrollmentDoesNotDuplicateTheAdmissionInvoice() {
        Course course = persistPricedCourse(1);
        enrollByStaff(persistStudent(), course);
        Student waiting = persistStudent();
        enrollByStaff(waiting, course);

        assertThatThrownBy(() -> enrollByStaff(waiting, course)).isInstanceOf(AlreadyEnrolledInCourseException.class);

        assertThat(applicationRepository.findByStudentIdOrderByIdDesc(waiting.getId())).hasSize(1);
    }

    @Test
    void waitlistedStaffEnrollmentWithApprovedAdmissionReceiptIsPromotedWhenASeatIsReleased() {
        Course course = persistPricedCourse(1);
        CourseEnrollment seated = enrollByStaff(persistStudent(), course);
        CourseEnrollment waiting = enrollByStaff(persistStudent(), course);
        approvePaymentReceiptUseCase.approve(uploadAdmissionReceipt(waiting), persistAdministrative(), PaymentVerification.none());

        releaseSeat(seated);

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(statusOf(waiting.getId())).isEqualTo(EnrollmentStatus.ENROLLED));
    }

    @Test
    void waitlistedStaffEnrollmentWithoutAnyAdmissionReceiptIsNotPromotedWhenASeatIsReleased() {
        Course course = persistPricedCourse(1);
        CourseEnrollment seated = enrollByStaff(persistStudent(), course);
        CourseEnrollment waiting = enrollByStaff(persistStudent(), course);

        releaseSeat(seated);

        assertThat(statusOf(waiting.getId())).isEqualTo(EnrollmentStatus.WAITLISTED);
    }

    @Test
    void waitlistedStaffEnrollmentWithPendingAdmissionReceiptIsNotPromotedWhenASeatIsReleased() {
        Course course = persistPricedCourse(1);
        CourseEnrollment seated = enrollByStaff(persistStudent(), course);
        CourseEnrollment waiting = enrollByStaff(persistStudent(), course);
        uploadAdmissionReceipt(waiting);

        releaseSeat(seated);

        assertThat(statusOf(waiting.getId())).isEqualTo(EnrollmentStatus.WAITLISTED);
    }
}
