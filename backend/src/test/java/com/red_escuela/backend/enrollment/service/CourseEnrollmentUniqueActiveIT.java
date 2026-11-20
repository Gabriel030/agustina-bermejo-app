package com.red_escuela.backend.enrollment.service;

import com.red_escuela.backend.academicstructure.entity.Course;
import com.red_escuela.backend.academicstructure.entity.CourseSubject;
import com.red_escuela.backend.academicstructure.entity.CourseSubjectSection;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.academicstructure.repository.CourseRepository;
import com.red_escuela.backend.academicstructure.repository.CourseSubjectRepository;
import com.red_escuela.backend.academicstructure.repository.CourseSubjectSectionRepository;
import com.red_escuela.backend.academicstructure.repository.TermRepository;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.config.ConcurrentRace;
import com.red_escuela.backend.config.ConcurrentRace.Outcome;
import com.red_escuela.backend.enrollment.dto.CreateCourseEnrollmentRequest;
import com.red_escuela.backend.enrollment.entity.CourseEnrollment;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.repository.CourseEnrollmentRepository;
import com.red_escuela.backend.identity.entity.Institution;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.identity.repository.AddressRepository;
import com.red_escuela.backend.identity.repository.InstitutionRepository;
import com.red_escuela.backend.identity.repository.PersonRepository;
import com.red_escuela.backend.identity.repository.StudentRepository;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.enrollment.exception.AlreadyEnrolledInCourseException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class CourseEnrollmentUniqueActiveIT extends AbstractIntegrationTest {

    private static final AtomicLong DNI_SEQUENCE = new AtomicLong(System.nanoTime() % 100_000_000L);

    @Autowired
    private CourseEnrollmentService courseEnrollmentService;
    @Autowired
    private CourseEnrollmentRepository courseEnrollmentRepository;
    @Autowired
    private CourseRepository courseRepository;
    @Autowired
    private CourseSubjectRepository courseSubjectRepository;
    @Autowired
    private CourseSubjectSectionRepository courseSubjectSectionRepository;
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

    private Student persistStudent() {
        String dni = String.valueOf(DNI_SEQUENCE.incrementAndGet());
        Person person = new Person();
        person.setName("Alumno");
        person.setLastname(dni);
        person.setDni(dni);
        person.setUsername(dni);
        person.setEmail(dni + "@example.com");
        person.setAddress(addressRepository.save(new com.red_escuela.backend.shared.domain.Address()));
        person.setStatus(StatusType.ACTIVE);
        Institution institution = new Institution();
        institution.setName("Instituto lock " + dni);
        Student student = new Student();
        student.setPerson(personRepository.save(person));
        student.setInstitution(institutionRepository.save(institution));
        student.setOnboardingStatus(StudentOnboardingStatus.CONFIRMADO);
        return studentRepository.save(student);
    }

    private CourseSubjectSection persistCourseWithOneSection(Course[] courseHolder) {
        Term term = com.red_escuela.backend.testsupport.TermFixtures.newTerm();
        term.setYear(2026);
        term.setStatus(StatusType.ACTIVE);
        term.setStartDate(LocalDateTime.now().plusMonths(1));
        Course course = new Course();
        course.setName("Curso de orden de locks " + System.nanoTime());
        course.setTerm(termRepository.save(term));
        course.setCapacity(100);
        course.setIsActive(StatusType.ACTIVE);
        course = courseRepository.save(course);
        CourseSubject module = new CourseSubject();
        module.setCourse(course);
        module.setName("Modulo unico");
        module.setSortOrder(1);
        module.setStatus(StatusType.ACTIVE);
        module = courseSubjectRepository.save(module);
        CourseSubjectSection section = new CourseSubjectSection();
        section.setName("Comision unica");
        section.setCourseSubject(module);
        section.setTerm(course.getTerm());
        section.setCapacity(100);
        section.setStatus(StatusType.ACTIVE);
        courseHolder[0] = course;
        return courseSubjectSectionRepository.save(section);
    }

    @Test
    void concurrentEnrollmentCreationLeavesOneActiveEnrollment() throws Exception {
        Course[] courseHolder = new Course[1];
        persistCourseWithOneSection(courseHolder);
        Long courseId = courseHolder[0].getId();
        Long studentId = persistStudent().getId();
        List<Callable<CourseEnrollment>> contenders = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            contenders.add(() -> courseEnrollmentService.enroll(studentId, new CreateCourseEnrollmentRequest(courseId, null)));
        }

        List<Outcome<CourseEnrollment>> outcomes = ConcurrentRace.run(contenders);

        assertThat(outcomes.stream().filter(Outcome::succeeded)).hasSize(1);
        assertThat(outcomes.stream().filter(outcome -> !outcome.succeeded()).map(Outcome::failure))
                .allMatch(AlreadyEnrolledInCourseException.class::isInstance);
        assertThat(courseEnrollmentRepository.findByStudentIdAndCourseIdAndStatusIn(studentId, courseId,
                List.of(EnrollmentStatus.ENROLLED, EnrollmentStatus.IN_PROGRESS, EnrollmentStatus.WAITLISTED))).hasSize(1);
    }

    @Test
    void concurrentDirectInsertsOfActiveCourseEnrollmentAreRejectedByTheIndex() throws Exception {
        Course[] courseHolder = new Course[1];
        persistCourseWithOneSection(courseHolder);
        Course course = courseHolder[0];
        Student student = persistStudent();
        List<Callable<Long>> contenders = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            contenders.add(() -> {
                CourseEnrollment enrollment = new CourseEnrollment();
                enrollment.setStudent(student);
                enrollment.setCourse(course);
                enrollment.setStatus(EnrollmentStatus.ENROLLED);
                enrollment.setEnrollmentDate(LocalDateTime.now());
                return courseEnrollmentRepository.saveAndFlush(enrollment).getId();
            });
        }

        List<Outcome<Long>> outcomes = ConcurrentRace.run(contenders);

        assertThat(outcomes.stream().filter(Outcome::succeeded)).hasSize(1);
        assertThat(outcomes.stream().filter(outcome -> !outcome.succeeded()).map(Outcome::failure))
                .allMatch(DataIntegrityViolationException.class::isInstance);
    }
}
