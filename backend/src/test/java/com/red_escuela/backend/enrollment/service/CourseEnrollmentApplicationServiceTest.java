package com.red_escuela.backend.enrollment.service;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.enrollment.dto.CreateCourseEnrollmentRequest;
import com.red_escuela.backend.entity.Address;
import com.red_escuela.backend.academicstructure.entity.Course;
import com.red_escuela.backend.enrollment.entity.CourseEnrollmentApplication;
import com.red_escuela.backend.billing.entity.FeeType;
import com.red_escuela.backend.academicstructure.entity.Institution;
import com.red_escuela.backend.billing.entity.InvoiceStatus;
import com.red_escuela.backend.billing.entity.PaymentReceiptStatus;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.billing.entity.PriceRevision;
import com.red_escuela.backend.identity.entity.Role;
import com.red_escuela.backend.entity.StatusType;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.enrollment.repository.CourseEnrollmentApplicationRepository;
import com.red_escuela.backend.enrollment.repository.CourseEnrollmentRepository;
import com.red_escuela.backend.identity.repository.RoleRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.red_escuela.backend.billing.usecase.paymentreceipt.UploadPaymentReceiptUseCase;
import com.red_escuela.backend.enrollment.service.CourseEnrollmentApplicationService;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CourseEnrollmentApplicationServiceTest extends AbstractIntegrationTest {
    @Autowired
    private CourseEnrollmentApplicationService courseEnrollmentApplicationService;

    @Autowired
    private CourseEnrollmentApplicationRepository courseEnrollmentApplicationRepository;

    @Autowired
    private CourseEnrollmentRepository courseEnrollmentRepository;

    @Autowired
    private com.red_escuela.backend.billing.usecase.paymentreceipt.UploadPaymentReceiptUseCase uploadPaymentReceiptUseCase;

    @Autowired
    private com.red_escuela.backend.billing.usecase.paymentreceipt.ApprovePaymentReceiptUseCase approvePaymentReceiptUseCase;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private EntityManager entityManager;

    private Institution persistInstitution() {
        Institution institution = new Institution();
        institution.setName("Instituto de prueba");
        entityManager.persist(institution);
        return institution;
    }

    private Person persistPersonBase(String dni) {
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

    private Student persistStudent(String dni) {
        Student student = new Student();
        student.setPerson(persistPersonBase(dni));
        student.setInstitution(persistInstitution());
        student.setOnboardingStatus(StudentOnboardingStatus.CONFIRMADO);
        entityManager.persist(student);
        return student;
    }

    private Course persistCourse(int capacity) {
        Term term = new Term();
        term.setYear(2026);
        term.setStatus(StatusType.ACTIVE);
        entityManager.persist(term);

        Course course = new Course();
        course.setName("Illustrator desde cero " + System.nanoTime());
        course.setTerm(term);
        course.setCapacity(capacity);
        course.setIsActive(StatusType.ACTIVE);
        entityManager.persist(course);
        return course;
    }

    private void persistPriceRevision(Course course, BigDecimal amount, Person createdBy) {
        PriceRevision revision = new PriceRevision();
        revision.setCourse(course);
        revision.setFeeType(FeeType.RECURRING_FEE);
        revision.setDescription("Precio de prueba");
        revision.setMonthlyAmount(amount);
        revision.setEffectiveFrom(LocalDate.now().minusDays(1));
        revision.setCreatedBy(createdBy);
        entityManager.persist(revision);
    }

    private MultipartFile receiptFile() {
        return new MockMultipartFile("comprobante", "comprobante.pdf", "application/pdf", "contenido de prueba".getBytes());
    }

    @Test
    void requestingWithPaymentDoesNotCreateTheCourseEnrollmentYet() {
        Student student = persistStudent("82000001");
        Course course = persistCourse(20);
        Person director = persistPersonBase("DIR" + System.nanoTime());
        persistPriceRevision(course, BigDecimal.valueOf(15000), director);
        entityManager.flush();

        CourseEnrollmentApplication application = courseEnrollmentApplicationService.requestWithPayment(
                student.getId(), new CreateCourseEnrollmentRequest(course.getId(), null), receiptFile());

        assertThat(application.getCourseEnrollment()).isNull();
        assertThat(application.getPaymentReceipt().getStatus()).isEqualTo(PaymentReceiptStatus.PENDING_REVIEW);
        assertThat(courseEnrollmentRepository.findByStudentId(student.getId())).isEmpty();
    }

    @Test
    void approvingTheFirstInstallmentReceiptTriggersTheActualEnrollment() {
        Student student = persistStudent("82000002");
        Course course = persistCourse(20);
        Person director = persistPersonBase("DIR" + System.nanoTime());
        persistPriceRevision(course, BigDecimal.valueOf(15000), director);
        entityManager.flush();

        CourseEnrollmentApplication application = courseEnrollmentApplicationService.requestWithPayment(
                student.getId(), new CreateCourseEnrollmentRequest(course.getId(), null), receiptFile());

        Person administrativo = persistPersonBase("ADMIN" + System.nanoTime());
        Role rolAdministrativo = roleRepository.findByName("ADMINISTRATIVO").orElseThrow();
        administrativo.getRoles().add(rolAdministrativo);
        entityManager.flush();

        approvePaymentReceiptUseCase.approve(application.getPaymentReceipt().getId(), administrativo);

        entityManager.flush();
        entityManager.clear();

        CourseEnrollmentApplication actualizada = entityManager.find(CourseEnrollmentApplication.class, application.getId());
        assertThat(actualizada.getCourseEnrollment()).isNotNull();
        assertThat(courseEnrollmentRepository.findByStudentId(student.getId())).hasSize(1);
        assertThat(actualizada.getPaymentReceipt().getInvoice().getStatus()).isEqualTo(InvoiceStatus.PAID);
        assertThat(actualizada.getPaymentReceipt().getInvoice().getCourseEnrollment()).isEqualTo(actualizada.getCourseEnrollment());

        Student estudianteActualizado = entityManager.find(Student.class, student.getId());
        assertThat(estudianteActualizado.getPerson().getRoles()).extracting(Role::getName).contains("ALUMNO");
    }

    @Test
    void requestingWithPaymentWithoutConfiguredPriceRevisionFails() {
        Student student = persistStudent("82000003");
        Course course = persistCourse(20);
        entityManager.flush();

        assertThatThrownBy(() -> courseEnrollmentApplicationService.requestWithPayment(
                student.getId(), new CreateCourseEnrollmentRequest(course.getId(), null), receiptFile()))
                .isInstanceOf(com.red_escuela.backend.billing.exception.PriceRevisionNotFoundException.class);
    }
}
