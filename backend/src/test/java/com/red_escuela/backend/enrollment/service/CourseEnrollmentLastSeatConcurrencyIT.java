package com.red_escuela.backend.enrollment.service;

import com.red_escuela.backend.academicstructure.entity.Course;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.academicstructure.repository.CourseRepository;
import com.red_escuela.backend.academicstructure.repository.TermRepository;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.enrollment.dto.CreateCourseEnrollmentRequest;
import com.red_escuela.backend.enrollment.entity.CourseEnrollment;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.identity.entity.Institution;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.identity.repository.AddressRepository;
import com.red_escuela.backend.identity.repository.InstitutionRepository;
import com.red_escuela.backend.identity.repository.PersonRepository;
import com.red_escuela.backend.identity.repository.StudentRepository;
import com.red_escuela.backend.shared.domain.StatusType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class CourseEnrollmentLastSeatConcurrencyIT extends AbstractIntegrationTest {

    @Autowired
    private CourseEnrollmentService courseEnrollmentService;
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

    private Student persistStudent(String dni) {
        Person person = new Person();
        person.setName("Alumno");
        person.setLastname(dni);
        person.setDni(dni);
        person.setUsername(dni);
        person.setEmail(dni + "@example.com");
        person.setAddress(addressRepository.save(new com.red_escuela.backend.shared.domain.Address()));
        person.setStatus(StatusType.ACTIVE);
        Institution institution = new Institution();
        institution.setName("Instituto concurrente " + dni);
        Student student = new Student();
        student.setPerson(personRepository.save(person));
        student.setInstitution(institutionRepository.save(institution));
        student.setOnboardingStatus(StudentOnboardingStatus.CONFIRMADO);
        return studentRepository.save(student);
    }

    private Course persistCourseWithSingleSeat() {
        Term term = new Term();
        term.setYear(2026);
        term.setStatus(StatusType.ACTIVE);
        term.setStartDate(LocalDateTime.now().plusMonths(1));
        Course course = new Course();
        course.setName("Curso de un cupo " + System.nanoTime());
        course.setTerm(termRepository.save(term));
        course.setCapacity(1);
        course.setIsActive(StatusType.ACTIVE);
        return courseRepository.save(course);
    }

    @Test
    void twoStudentsEnrollingConcurrentlyInTheLastSeatLeaveExactlyOneEnrolled() throws Exception {
        Course course = persistCourseWithSingleSeat();
        long suffix = System.nanoTime() % 1_000_000L;
        List<Long> studentIds = List.of(
                persistStudent("81" + String.format("%06d", suffix)).getId(),
                persistStudent("82" + String.format("%06d", suffix)).getId());

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startSignal = new CountDownLatch(1);
        List<Future<CourseEnrollment>> results = new ArrayList<>();
        for (Long studentId : studentIds) {
            Callable<CourseEnrollment> enrollment = () -> {
                startSignal.await();
                return courseEnrollmentService.enroll(studentId, new CreateCourseEnrollmentRequest(course.getId(), null));
            };
            results.add(executor.submit(enrollment));
        }
        startSignal.countDown();
        List<EnrollmentStatus> statuses = new ArrayList<>();
        try {
            for (Future<CourseEnrollment> result : results) {
                statuses.add(result.get(30, TimeUnit.SECONDS).getStatus());
            }
        } catch (ExecutionException e) {
            throw new AssertionError("concurrent enrollment failed", e.getCause());
        } finally {
            executor.shutdownNow();
        }

        assertThat(statuses).containsExactlyInAnyOrder(EnrollmentStatus.ENROLLED, EnrollmentStatus.WAITLISTED);
    }
}
