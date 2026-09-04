package com.red_escuela.backend.service;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.dto.CreateCourseEnrollmentRequest;
import com.red_escuela.backend.entity.Address;
import com.red_escuela.backend.entity.Course;
import com.red_escuela.backend.entity.CourseEnrollment;
import com.red_escuela.backend.entity.EnrollmentStatus;
import com.red_escuela.backend.entity.Institution;
import com.red_escuela.backend.entity.Notification;
import com.red_escuela.backend.entity.NotificationType;
import com.red_escuela.backend.entity.Person;
import com.red_escuela.backend.entity.StatusType;
import com.red_escuela.backend.entity.Student;
import com.red_escuela.backend.entity.StudentOnboardingStatus;
import com.red_escuela.backend.entity.Term;
import com.red_escuela.backend.repository.AddressRepository;
import com.red_escuela.backend.repository.CourseEnrollmentRepository;
import com.red_escuela.backend.repository.CourseRepository;
import com.red_escuela.backend.repository.InstitutionRepository;
import com.red_escuela.backend.repository.NotificationRepository;
import com.red_escuela.backend.repository.PersonRepository;
import com.red_escuela.backend.repository.StudentRepository;
import com.red_escuela.backend.repository.TermRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Decisión 2026-09-03: Course.capacity pasa de campo decorativo a un cupo real, con lista de
 * espera FIFO (mismo patrón que CourseSubjectSection/SubjectSection) y corte configurable
 * (Course.waitlistDeadlineOffsetDays) que CourseEnrollmentWaitlistExpiryJob hace cumplir. Cubre
 * el gap señalado explícitamente como deuda de testing en RegistrationFlowTest.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CourseEnrollmentServiceTest extends AbstractIntegrationTest {

    @Autowired
    private CourseEnrollmentService courseEnrollmentService;

    @Autowired
    private CourseEnrollmentWaitlistExpiryJob waitlistExpiryJob;

    @Autowired
    private CourseEnrollmentRepository courseEnrollmentRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private TermRepository termRepository;

    @Autowired
    private InstitutionRepository institutionRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private PersonRepository personRepository;

    @Autowired
    private AddressRepository addressRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    private Student persistStudent(String dni) {
        Address address = addressRepository.save(new Address());
        Person person = new Person();
        person.setName("Alumno");
        person.setLastname(dni);
        person.setDni(dni);
        person.setUsername(dni);
        person.setEmail(dni + "@example.com");
        person.setAddress(address);
        person.setStatus(StatusType.ACTIVE);
        person = personRepository.save(person);

        Institution institution = new Institution();
        institution.setName("Instituto de prueba " + System.nanoTime());
        institution = institutionRepository.save(institution);

        Student student = new Student();
        student.setPerson(person);
        student.setInstitution(institution);
        student.setOnboardingStatus(StudentOnboardingStatus.CONFIRMADO);
        return studentRepository.save(student);
    }

    private Term persistTerm(LocalDateTime startDate) {
        Term term = new Term();
        term.setYear(2026);
        term.setStatus(StatusType.ACTIVE);
        term.setStartDate(startDate);
        return termRepository.save(term);
    }

    private Course persistCourse(Term term, Integer capacity, Integer waitlistDeadlineOffsetDays) {
        Course course = new Course();
        course.setName("Curso con cupo " + System.nanoTime());
        course.setTerm(term);
        course.setCapacity(capacity);
        course.setIsActive(StatusType.ACTIVE);
        course.setWaitlistDeadlineOffsetDays(waitlistDeadlineOffsetDays);
        return courseRepository.save(course);
    }

    // Sin CourseSubject/CourseSubjectSection: el curso no tiene módulos configurados, así que
    // matricular() itera una lista vacía y no crea ninguna CourseSubjectSectionEnrollment — no
    // afecta lo que se está probando acá (el cupo del CURSO, no el de sus comisiones).
    @Test
    void fourthEnrolleeIsWaitlistedWhenCapacityIsThree() {
        Term term = persistTerm(LocalDateTime.now().plusMonths(1));
        Course course = persistCourse(term, 3, null);

        CourseEnrollment e1 = courseEnrollmentService.matricular(
                persistStudent("90000001").getId(), new CreateCourseEnrollmentRequest(course.getId(), null));
        CourseEnrollment e2 = courseEnrollmentService.matricular(
                persistStudent("90000002").getId(), new CreateCourseEnrollmentRequest(course.getId(), null));
        CourseEnrollment e3 = courseEnrollmentService.matricular(
                persistStudent("90000003").getId(), new CreateCourseEnrollmentRequest(course.getId(), null));
        CourseEnrollment e4 = courseEnrollmentService.matricular(
                persistStudent("90000004").getId(), new CreateCourseEnrollmentRequest(course.getId(), null));

        assertThat(e1.getStatus()).isEqualTo(EnrollmentStatus.ENROLLED);
        assertThat(e2.getStatus()).isEqualTo(EnrollmentStatus.ENROLLED);
        assertThat(e3.getStatus()).isEqualTo(EnrollmentStatus.ENROLLED);
        assertThat(e4.getStatus()).isEqualTo(EnrollmentStatus.WAITLISTED);
    }

    @Test
    void noCapConfiguredMeansNoLimit() {
        Term term = persistTerm(LocalDateTime.now().plusMonths(1));
        Course course = persistCourse(term, null, null);

        for (int i = 0; i < 5; i++) {
            CourseEnrollment enrollment = courseEnrollmentService.matricular(
                    persistStudent("9100000" + i).getId(), new CreateCourseEnrollmentRequest(course.getId(), null));
            assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.ENROLLED);
        }
    }

    @Test
    void withdrawalPromotesFirstInCourseWaitlist() {
        Term term = persistTerm(LocalDateTime.now().plusMonths(1));
        Course course = persistCourse(term, 1, null);

        CourseEnrollment primero = courseEnrollmentService.matricular(
                persistStudent("92000001").getId(), new CreateCourseEnrollmentRequest(course.getId(), null));
        CourseEnrollment segundo = courseEnrollmentService.matricular(
                persistStudent("92000002").getId(), new CreateCourseEnrollmentRequest(course.getId(), null));
        assertThat(primero.getStatus()).isEqualTo(EnrollmentStatus.ENROLLED);
        assertThat(segundo.getStatus()).isEqualTo(EnrollmentStatus.WAITLISTED);

        courseEnrollmentService.darDeBaja(primero.getId());

        CourseEnrollment segundoRecargado = courseEnrollmentRepository.findById(segundo.getId()).orElseThrow();
        assertThat(segundoRecargado.getStatus()).isEqualTo(EnrollmentStatus.ENROLLED);
    }

    // Alguien en lista de espera del curso se da de baja voluntariamente ANTES del corte —
    // no debe promover a nadie, porque nunca ocupó el cupo (WAITLISTED no cuenta en
    // calculateStatusByCourseCapacity).
    @Test
    void withdrawingWhileWaitlistedPromotesNoOne() {
        Term term = persistTerm(LocalDateTime.now().plusMonths(1));
        Course course = persistCourse(term, 1, null);

        CourseEnrollment primero = courseEnrollmentService.matricular(
                persistStudent("93000001").getId(), new CreateCourseEnrollmentRequest(course.getId(), null));
        CourseEnrollment segundo = courseEnrollmentService.matricular(
                persistStudent("93000002").getId(), new CreateCourseEnrollmentRequest(course.getId(), null));
        assertThat(segundo.getStatus()).isEqualTo(EnrollmentStatus.WAITLISTED);

        courseEnrollmentService.darDeBaja(segundo.getId());

        CourseEnrollment primeroRecargado = courseEnrollmentRepository.findById(primero.getId()).orElseThrow();
        assertThat(primeroRecargado.getStatus()).isEqualTo(EnrollmentStatus.ENROLLED);
        CourseEnrollment segundoRecargado = courseEnrollmentRepository.findById(segundo.getId()).orElseThrow();
        assertThat(segundoRecargado.getStatus()).isEqualTo(EnrollmentStatus.WITHDRAWN);
    }

    @Test
    void jobCancelsExpiredWaitlistAndNotifiesStudent() {
        // Corte = term.startDate - 5 días, y term.startDate ya pasó hace 10 días -> corte ya
        // pasó también (hace 5 días) -> el job tiene que cancelar.
        Term term = persistTerm(LocalDateTime.now().minusDays(10));
        Course course = persistCourse(term, 1, -5);

        CourseEnrollment primero = courseEnrollmentService.matricular(
                persistStudent("94000001").getId(), new CreateCourseEnrollmentRequest(course.getId(), null));
        CourseEnrollment segundo = courseEnrollmentService.matricular(
                persistStudent("94000002").getId(), new CreateCourseEnrollmentRequest(course.getId(), null));
        assertThat(segundo.getStatus()).isEqualTo(EnrollmentStatus.WAITLISTED);

        waitlistExpiryJob.expireWaitlists();

        CourseEnrollment segundoRecargado = courseEnrollmentRepository.findById(segundo.getId()).orElseThrow();
        assertThat(segundoRecargado.getStatus()).isEqualTo(EnrollmentStatus.WAITLIST_EXPIRED);
        // El primero (ENROLLED, con su cupo) no se toca.
        CourseEnrollment primeroRecargado = courseEnrollmentRepository.findById(primero.getId()).orElseThrow();
        assertThat(primeroRecargado.getStatus()).isEqualTo(EnrollmentStatus.ENROLLED);

        List<Notification> notificaciones = notificationRepository.findByRecipientIdOrderByCreatedAtDesc(
                segundo.getStudent().getPerson().getId());
        assertThat(notificaciones).hasSize(1);
        assertThat(notificaciones.get(0).getType()).isEqualTo(NotificationType.COURSE_WAITLIST_EXPIRED);
    }

    @Test
    void jobDoesNotTouchWaitlistsBeforeDeadline() {
        // Corte = term.startDate + 5 días, term.startDate es en 1 mes -> el corte todavía no llegó.
        Term term = persistTerm(LocalDateTime.now().plusMonths(1));
        Course course = persistCourse(term, 1, 5);

        courseEnrollmentService.matricular(
                persistStudent("95000001").getId(), new CreateCourseEnrollmentRequest(course.getId(), null));
        CourseEnrollment segundo = courseEnrollmentService.matricular(
                persistStudent("95000002").getId(), new CreateCourseEnrollmentRequest(course.getId(), null));

        waitlistExpiryJob.expireWaitlists();

        CourseEnrollment segundoRecargado = courseEnrollmentRepository.findById(segundo.getId()).orElseThrow();
        assertThat(segundoRecargado.getStatus()).isEqualTo(EnrollmentStatus.WAITLISTED);
    }
}
