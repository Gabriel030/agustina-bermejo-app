package com.red_escuela.backend.billing.collections.service;

import com.red_escuela.backend.academicstructure.entity.Course;
import com.red_escuela.backend.identity.entity.Institution;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.academicstructure.repository.CourseRepository;
import com.red_escuela.backend.identity.repository.InstitutionRepository;
import com.red_escuela.backend.academicstructure.repository.TermRepository;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.support.BillingTestFixtures;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.enrollment.entity.CourseEnrollment;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.repository.CourseEnrollmentRepository;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.identity.repository.AddressRepository;
import com.red_escuela.backend.identity.repository.PersonRepository;
import com.red_escuela.backend.identity.repository.RoleRepository;
import com.red_escuela.backend.identity.repository.StudentRepository;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.Money;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.shared.time.ApplicationTime;
import com.red_escuela.backend.shared.time.MutableClock;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;

import static com.red_escuela.backend.shared.domain.TestMoney.ars;

public abstract class AbstractOverdueContactIT extends AbstractIntegrationTest {

    protected static final LocalDate MARCH_ELEVENTH = LocalDate.of(2027, 3, 11);
    protected static final LocalDate MARCH_TWENTY_FIFTH = LocalDate.of(2027, 3, 25);

    @Autowired
    protected JdbcTemplate jdbc;
    @Autowired
    protected TransactionTemplate transactionTemplate;
    @Autowired
    private AddressRepository addressRepository;
    @Autowired
    private PersonRepository personRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private InstitutionRepository institutionRepository;
    @Autowired
    private StudentRepository studentRepository;
    @Autowired
    private TermRepository termRepository;
    @Autowired
    private CourseRepository courseRepository;
    @Autowired
    private CourseEnrollmentRepository courseEnrollmentRepository;

    private Institution institution;
    private Term term;

    @BeforeEach
    void setUpOverdueContactFixtures() {
        ApplicationTime.useClock(MutableClock.INSTANCE);
        MutableClock.INSTANCE.setDateTime(LocalDateTime.of(2027, 2, 26, 12, 0));
        institution = new Institution();
        institution.setName("Instituto para contactar " + System.nanoTime());
        institution = institutionRepository.save(institution);
        term = new Term();
        term.setYear(2027 + (int) (System.nanoTime() % 1000));
        term.setStatus(StatusType.ACTIVE);
        term = termRepository.save(term);
    }

    protected Person newPerson(String prefix, String role) {
        return transactionTemplate.execute(status -> {
            Person person = new Person();
            String username = prefix + "." + System.nanoTime();
            person.setUsername(username);
            person.setDni(Long.toString(Math.abs((long) username.hashCode())));
            person.setName("Persona");
            person.setLastname("Contacto " + prefix);
            person.setEmail(username + "@example.com");
            person.setAddress(addressRepository.save(new Address()));
            person.setStatus(StatusType.ACTIVE);
            if (role != null) {
                person.getRoles().add(roleRepository.findByName(role).orElseThrow());
            }
            return personRepository.save(person);
        });
    }

    protected Student newStudent() {
        Student student = new Student();
        student.setPerson(newPerson("alumno", null));
        student.setInstitution(institution);
        student.setOnboardingStatus(StudentOnboardingStatus.CONFIRMADO);
        return studentRepository.save(student);
    }

    protected CourseEnrollment newCourseEnrollment(Student student) {
        Course course = new Course();
        course.setName("Curso para contactar " + System.nanoTime());
        course.setTerm(term);
        course.setIsActive(StatusType.ACTIVE);
        CourseEnrollment enrollment = new CourseEnrollment();
        enrollment.setStudent(student);
        enrollment.setCourse(courseRepository.save(course));
        enrollment.setStatus(EnrollmentStatus.ENROLLED);
        enrollment.setEnrollmentDate(LocalDateTime.of(2027, 2, 1, 10, 0));
        return courseEnrollmentRepository.save(enrollment);
    }

    protected Invoice installmentDueOn(CourseEnrollment enrollment, LocalDate dueDate, Money amount) {
        Invoice invoice = BillingTestFixtures.invoice().forCourseEnrollment(enrollment).forPeriod(YearMonth.from(dueDate)).dueOn(dueDate).withBaseAmount(amount).build();
        jdbc.update("update invoice set issue_date = ?, due_date = ? where id = ?", dueDate.minusDays(10), dueDate, invoice.getId());
        return invoice;
    }

    protected Invoice installmentDueOn(CourseEnrollment enrollment, LocalDate dueDate) {
        return installmentDueOn(enrollment, dueDate, ars(100000));
    }

    protected void putReceiptUnderReview(Invoice invoice) {
        jdbc.update("insert into bank_transfer_receipt (invoice_id, file_path, uploaded_at, status, version) values (?, 'comprobante.pdf', timestamp '2099-01-01 10:00:00', 'PENDING_REVIEW', 0)",
                invoice.getId());
    }
}
