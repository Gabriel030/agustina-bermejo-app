package com.red_escuela.backend.service;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.dto.CreateCourseEnrollmentRequest;
import com.red_escuela.backend.entity.Address;
import com.red_escuela.backend.entity.Course;
import com.red_escuela.backend.entity.CourseEnrollmentApplication;
import com.red_escuela.backend.billing.entity.FeeType;
import com.red_escuela.backend.entity.Institution;
import com.red_escuela.backend.billing.entity.InvoiceStatus;
import com.red_escuela.backend.billing.entity.PaymentReceiptStatus;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.billing.entity.PriceRevision;
import com.red_escuela.backend.identity.entity.Role;
import com.red_escuela.backend.entity.StatusType;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.entity.Term;
import com.red_escuela.backend.repository.CourseEnrollmentApplicationRepository;
import com.red_escuela.backend.repository.CourseEnrollmentRepository;
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
import com.red_escuela.backend.billing.service.PaymentReceiptService;

/**
 * Épica 7 Fase 3: inscripción a curso con pago de la primera cuota. A diferencia de
 * carrera, no hay una "solicitud" que alguien apruebe/rechace — el CourseEnrollment
 * real recién se crea cuando PaymentReceiptService.aprobar aprueba el comprobante
 * (ver PaymentReceiptService, javadoc). PaymentReceiptService es un placeholder mínimo
 * hasta Fase 6.2 (Pagos y allocations) — acá alcanza con verificar que la Invoice pasa
 * a PAID, sin un Payment real todavía.
 */
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
    private PaymentReceiptService paymentReceiptService;

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

    private Course persistCurso(int capacity) {
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

    private MultipartFile comprobante() {
        return new MockMultipartFile("comprobante", "comprobante.pdf", "application/pdf", "contenido de prueba".getBytes());
    }

    @Test
    void solicitarConPagoNoCreaElCourseEnrollmentTodavia() {
        Student student = persistStudent("82000001");
        Course course = persistCurso(20);
        Person director = persistPersonBase("DIR" + System.nanoTime());
        persistPriceRevision(course, BigDecimal.valueOf(15000), director);
        entityManager.flush();

        CourseEnrollmentApplication application = courseEnrollmentApplicationService.solicitarConPago(
                student.getId(), new CreateCourseEnrollmentRequest(course.getId(), null), comprobante());

        assertThat(application.getCourseEnrollment()).isNull();
        assertThat(application.getPaymentReceipt().getStatus()).isEqualTo(PaymentReceiptStatus.PENDING_REVIEW);
        assertThat(courseEnrollmentRepository.findByStudentId(student.getId())).isEmpty();
    }

    @Test
    void aprobarElComprobanteDeLaPrimeraCuotaDisparaLaMatriculacionReal() {
        Student student = persistStudent("82000002");
        Course course = persistCurso(20);
        Person director = persistPersonBase("DIR" + System.nanoTime());
        persistPriceRevision(course, BigDecimal.valueOf(15000), director);
        entityManager.flush();

        CourseEnrollmentApplication application = courseEnrollmentApplicationService.solicitarConPago(
                student.getId(), new CreateCourseEnrollmentRequest(course.getId(), null), comprobante());

        Person administrativo = persistPersonBase("ADMIN" + System.nanoTime());
        Role rolAdministrativo = roleRepository.findByName("ADMINISTRATIVO").orElseThrow();
        administrativo.getRoles().add(rolAdministrativo);
        entityManager.flush();

        paymentReceiptService.aprobar(application.getPaymentReceipt().getId(), administrativo);

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
    void solicitarConPagoSinPriceRevisionConfiguradaFalla() {
        Student student = persistStudent("82000003");
        Course course = persistCurso(20);
        entityManager.flush();

        assertThatThrownBy(() -> courseEnrollmentApplicationService.solicitarConPago(
                student.getId(), new CreateCourseEnrollmentRequest(course.getId(), null), comprobante()))
                .isInstanceOf(com.red_escuela.backend.billing.exception.PriceRevisionNotFoundException.class);
    }
}
